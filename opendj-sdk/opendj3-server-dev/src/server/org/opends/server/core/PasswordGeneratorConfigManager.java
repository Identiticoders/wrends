/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.PasswordGeneratorCfgDefn;
import org.opends.server.admin.std.server.PasswordGeneratorCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.InitializationException;
import org.forgerock.opendj.ldap.ResultCode;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a utility that will be used to manage the set of password
 * generators defined in the Directory Server.  It will initialize the
 * generators when the server starts, and then will manage any additions,
 * removals, or modifications to any password generators while the server is
 * running.
 */
public class PasswordGeneratorConfigManager
       implements ConfigurationAddListener<PasswordGeneratorCfg>,
       ConfigurationDeleteListener<PasswordGeneratorCfg>,
       ConfigurationChangeListener<PasswordGeneratorCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();


  // A mapping between the DNs of the config entries and the associated password
  // generators.
  private ConcurrentHashMap<DN,PasswordGenerator> passwordGenerators;


  /**
   * Creates a new instance of this password generator config manager.
   */
  public PasswordGeneratorConfigManager()
  {
    passwordGenerators = new ConcurrentHashMap<DN,PasswordGenerator>();
  }



  /**
   * Initializes all password generators currently defined in the Directory
   * Server configuration.  This should only be called at Directory Server
   * startup.
   *
   * @throws  ConfigException  If a configuration problem causes the password
   *                           generator initialization process to fail.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password generators that is not
   *                                   related to the server configuration.
   */
  public void initializePasswordGenerators()
         throws ConfigException, InitializationException
  {
    // Get the root configuration object.
    ServerManagementContext managementContext =
         ServerManagementContext.getInstance();
    RootCfg rootConfiguration =
         managementContext.getRootConfiguration();

    // Register as an add and delete listener with the root configuration so we
    // can be notified if any password generator entries are added or removed.
    rootConfiguration.addPasswordGeneratorAddListener(this);
    rootConfiguration.addPasswordGeneratorDeleteListener(this);


    //Initialize the existing password generators.
    for (String generatorName : rootConfiguration.listPasswordGenerators())
    {
      PasswordGeneratorCfg generatorConfiguration =
           rootConfiguration.getPasswordGenerator(generatorName);
      generatorConfiguration.addChangeListener(this);

      if (generatorConfiguration.isEnabled())
      {
        String className = generatorConfiguration.getJavaClass();
        try
        {
          PasswordGenerator<? extends PasswordGeneratorCfg>
               generator = loadGenerator(className, generatorConfiguration,
                                         true);
          passwordGenerators.put(generatorConfiguration.dn(), generator);
          DirectoryServer.registerPasswordGenerator(generatorConfiguration.dn(),
              generator);
        }
        catch (InitializationException ie)
        {
          logger.error(ie.getMessageObject());
          continue;
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      PasswordGeneratorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password generator.
      String className = configuration.getJavaClass();
      try
      {
        loadGenerator(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                                 PasswordGeneratorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<LocalizableMessage> messages            = new ArrayList<LocalizableMessage>();


    // Get the existing generator if it's already enabled.
    PasswordGenerator existingGenerator =
         passwordGenerators.get(configuration.dn());


    // If the new configuration has the generator disabled, then disable it if
    // it is enabled, or do nothing if it's already disabled.
    if (! configuration.isEnabled())
    {
      if (existingGenerator != null)
      {
        DirectoryServer.deregisterPasswordGenerator(configuration.dn());

        PasswordGenerator passwordGenerator =
             passwordGenerators.remove(configuration.dn());
        if (passwordGenerator != null)
        {
          passwordGenerator.finalizePasswordGenerator();
        }
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }


    // Get the class for the password generator.  If the generator is already
    // enabled, then we shouldn't do anything with it although if the class has
    // changed then we'll at least need to indicate that administrative action
    // is required.  If the generator is disabled, then instantiate the class
    // and initialize and register it as a password generator.
    String className = configuration.getJavaClass();
    if (existingGenerator != null)
    {
      if (! className.equals(existingGenerator.getClass().getName()))
      {
        adminActionRequired = true;
      }

      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordGenerator<? extends PasswordGeneratorCfg>
         passwordGenerator = null;
    try
    {
      passwordGenerator = loadGenerator(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      passwordGenerators.put(configuration.dn(), passwordGenerator);
      DirectoryServer.registerPasswordGenerator(configuration.dn(),
                                                passwordGenerator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
                      PasswordGeneratorCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    if (configuration.isEnabled())
    {
      // Get the name of the class and make sure we can instantiate it as a
      // password generator.
      String className = configuration.getJavaClass();
      try
      {
        loadGenerator(className, configuration, false);
      }
      catch (InitializationException ie)
      {
        unacceptableReasons.add(ie.getMessageObject());
        return false;
      }
    }

    // If we've gotten here, then it's fine.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
                                 PasswordGeneratorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<LocalizableMessage> messages            = new ArrayList<LocalizableMessage>();

    configuration.addChangeListener(this);

    if (! configuration.isEnabled())
    {
      return new ConfigChangeResult(resultCode, adminActionRequired, messages);
    }

    PasswordGenerator<? extends PasswordGeneratorCfg>
         passwordGenerator = null;

    // Get the name of the class and make sure we can instantiate it as a
    // password generator.
    String className = configuration.getJavaClass();
    try
    {
      passwordGenerator = loadGenerator(className, configuration, true);
    }
    catch (InitializationException ie)
    {
      if (resultCode == ResultCode.SUCCESS)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      messages.add(ie.getMessageObject());
    }

    if (resultCode == ResultCode.SUCCESS)
    {
      passwordGenerators.put(configuration.dn(), passwordGenerator);
      DirectoryServer.registerPasswordGenerator(configuration.dn(),
                                                passwordGenerator);
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      PasswordGeneratorCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    // A delete should always be acceptable, so just return true.
    return true;
  }


  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      PasswordGeneratorCfg configuration)
  {
    ResultCode resultCode          = ResultCode.SUCCESS;
    boolean    adminActionRequired = false;


    // See if the entry is registered as a password generator.  If so,
    // deregister it and stop the generator.
    PasswordGenerator generator = passwordGenerators.remove(configuration.dn());
    if (generator != null)
    {
      DirectoryServer.deregisterPasswordGenerator(configuration.dn());

      generator.finalizePasswordGenerator();
    }


    return new ConfigChangeResult(resultCode, adminActionRequired);
  }

  /**
   * Loads the specified class, instantiates it as a password generator, and
   * optionally initializes that instance.
   *
   * @param  className      The fully-qualified name of the password generator
   *                        class to load, instantiate, and initialize.
   * @param  configuration  The configuration to use to initialize the
   *                        password generator, or {@code null} if the
   *                        password generator should not be initialized.
   * @param  initialize     Indicates whether the password generator instance
   *                        should be initialized.
   *
   * @return  The possibly initialized password generator.
   *
   * @throws  InitializationException  If a problem occurred while attempting to
   *                                   initialize the password generator.
   */
  private <T extends PasswordGeneratorCfg> PasswordGenerator<T>
               loadGenerator(String className,
                             T configuration,
                             boolean initialize)
          throws InitializationException
  {
    try
    {
      PasswordGeneratorCfgDefn definition =
           PasswordGeneratorCfgDefn.getInstance();
      ClassPropertyDefinition propertyDefinition =
           definition.getJavaClassPropertyDefinition();
      Class<? extends PasswordGenerator> generatorClass =
           propertyDefinition.loadClass(className, PasswordGenerator.class);
      PasswordGenerator<T> generator = generatorClass.newInstance();

      if (initialize)
      {
        generator.initializePasswordGenerator(configuration);
      }
      else
      {
        List<LocalizableMessage> unacceptableReasons = new ArrayList<LocalizableMessage>();
        if (!generator.isConfigurationAcceptable(configuration, unacceptableReasons))
        {
          String reasons = Utils.joinAsString(".  ", unacceptableReasons);
          throw new InitializationException(
              ERR_CONFIG_PWGENERATOR_CONFIG_NOT_ACCEPTABLE.get(configuration.dn(), reasons));
        }
      }

      return generator;
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_PWGENERATOR_INITIALIZATION_FAILED.
          get(className, configuration.dn(), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }
  }
}

