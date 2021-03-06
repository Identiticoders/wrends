/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.ClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.CancelResult;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.RawModification;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PreParseModifyOperation;
import org.opends.server.workflowelement.localbackend.LocalBackendModifyOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement.*;

/** This class defines an operation that may be used to modify an entry in the Directory Server. */
public class ModifyOperationBasis
       extends AbstractOperation implements ModifyOperation,
       PreParseModifyOperation,
       PostResponseModifyOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The raw, unprocessed entry DN as included by the client request. */
  private ByteString rawEntryDN;

  /** The DN of the entry for the modify operation. */
  private DN entryDN;

  /** The proxied authorization target DN for this operation. */
  private DN proxiedAuthorizationDN;

  /** The set of response controls for this modify operation. */
  private List<Control> responseControls;

  /** The raw, unprocessed set of modifications as included in the client request. */
  private List<RawModification> rawModifications;

  /** The set of modifications for this modify operation. */
  private List<Modification> modifications;

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  rawEntryDN        The raw, unprocessed DN of the entry to modify,
   *                           as included in the client request.
   * @param  rawModifications  The raw, unprocessed set of modifications for
   *                           this modify operation as included in the client
   *                           request.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      ByteString rawEntryDN,
      List<RawModification> rawModifications)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.rawEntryDN       = rawEntryDN;
    this.rawModifications = rawModifications;

    entryDN          = null;
    modifications    = null;
    responseControls = new ArrayList<>();
    cancelRequest    = null;
  }

  /**
   * Creates a new modify operation with the provided information.
   *
   * @param  clientConnection  The client connection with which this operation
   *                           is associated.
   * @param  operationID       The operation ID for this operation.
   * @param  messageID         The message ID of the request with which this
   *                           operation is associated.
   * @param  requestControls   The set of controls included in the request.
   * @param  entryDN           The entry DN for the modify operation.
   * @param  modifications     The set of modifications for this modify
   *                           operation.
   */
  public ModifyOperationBasis(ClientConnection clientConnection,
      long operationID,
      int messageID, List<Control> requestControls,
      DN entryDN, List<Modification> modifications)
  {
    super(clientConnection, operationID, messageID, requestControls);

    this.entryDN       = entryDN;
    this.modifications = modifications;

    rawEntryDN = ByteString.valueOfUtf8(entryDN.toString());

    rawModifications = new ArrayList<>(modifications.size());
    for (Modification m : modifications)
    {
      rawModifications.add(new LDAPModification(m.getModificationType(),
          new LDAPAttribute(m.getAttribute())));
    }

    responseControls = new ArrayList<>();
    cancelRequest    = null;
  }

  @Override
  public final ByteString getRawEntryDN()
  {
    return rawEntryDN;
  }

  @Override
  public final void setRawEntryDN(ByteString rawEntryDN)
  {
    this.rawEntryDN = rawEntryDN;

    entryDN = null;
  }

  @Override
  public final DN getEntryDN()
  {
    if (entryDN == null){
      try {
        entryDN = DN.valueOf(rawEntryDN);
      }
      catch (LocalizedIllegalArgumentException e) {
        logger.traceException(e);

        setResultCode(ResultCode.INVALID_DN_SYNTAX);
        appendErrorMessage(e.getMessageObject());
      }
    }
    return entryDN;
  }

  @Override
  public final List<RawModification> getRawModifications()
  {
    return rawModifications;
  }

  @Override
  public final void addRawModification(RawModification rawModification)
  {
    rawModifications.add(rawModification);

    modifications = null;
  }

  @Override
  public final void setRawModifications(List<RawModification> rawModifications)
  {
    this.rawModifications = rawModifications;

    modifications = null;
  }

  @Override
  public final List<Modification> getModifications()
  {
    if (modifications == null)
    {
      modifications = new ArrayList<>(rawModifications.size());
      try {
        for (RawModification m : rawModifications)
        {
           Modification mod = m.toModification();
           Attribute attr = mod.getAttribute();
           AttributeDescription attrDesc = attr.getAttributeDescription();

           boolean hasBinaryOption = attrDesc.hasOption("binary");
           if (attrDesc.getAttributeType().getSyntax().isBEREncodingRequired())
           {
             if (!hasBinaryOption)
             {
               //A binary option wasn't provided by the client so add it.
               AttributeBuilder builder = new AttributeBuilder(attr);
               builder.setOption("binary");
               mod.setAttribute(builder.toAttribute());
             }
           }
           else if (hasBinaryOption)
           {
             // binary option is not honored for non-BER-encodable attributes.
             throw new LDAPException(LDAPResultCode.UNDEFINED_ATTRIBUTE_TYPE,
                 ERR_ADD_ATTR_IS_INVALID_OPTION.get(entryDN, attrDesc));
           }

           modifications.add(mod);
        }
      }
      catch (LDAPException le)
      {
        logger.traceException(le);
        setResultCode(ResultCode.valueOf(le.getResultCode()));
        appendErrorMessage(le.getMessageObject());
        modifications = null;
      }
    }
    return modifications;
  }

  @Override
  public final void addModification(Modification modification)
  throws DirectoryException
  {
    modifications.add(modification);
  }

  @Override
  public final OperationType getOperationType()
  {
    // Note that no debugging will be done in this method because it is a likely
    // candidate for being called by the logging subsystem.

    return OperationType.MODIFY;
  }

  @Override
  public DN getProxiedAuthorizationDN()
  {
    return proxiedAuthorizationDN;
  }

  @Override
  public final List<Control> getResponseControls()
  {
    return responseControls;
  }

  @Override
  public final void addResponseControl(Control control)
  {
    responseControls.add(control);
  }

  @Override
  public final void removeResponseControl(Control control)
  {
    responseControls.remove(control);
  }

  @Override
  public final void toString(StringBuilder buffer)
  {
    buffer.append("ModifyOperation(connID=");
    buffer.append(clientConnection.getConnectionID());
    buffer.append(", opID=");
    buffer.append(operationID);
    buffer.append(", dn=");
    buffer.append(rawEntryDN);
    buffer.append(")");
  }

  @Override
  public void setProxiedAuthorizationDN(DN proxiedAuthorizationDN)
  {
    this.proxiedAuthorizationDN = proxiedAuthorizationDN;
  }

  @Override
  public final void run()
  {
    setResultCode(ResultCode.UNDEFINED);

    // Start the processing timer.
    setProcessingStartTime();

    logModifyRequest(this);

    // This flag is set to true as soon as a workflow has been executed.
    boolean workflowExecuted = false;
    try
    {
      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Invoke the pre-parse modify plugins.
      if (!processOperationResult(getPluginConfigManager().invokePreParseModifyPlugins(this)))
      {
        return;
      }

      // Check for and handle a request to cancel this operation.
      checkIfCanceled(false);

      // Process the entry DN to convert it from the raw form to the form
      // required for the rest of the modify processing.
      DN entryDN = getEntryDN();
      if (entryDN == null){
        return;
      }

      workflowExecuted = execute(this, entryDN);
    }
    catch(CanceledOperationException coe)
    {
      logger.traceException(coe);

      setResultCode(ResultCode.CANCELLED);
      cancelResult = new CancelResult(ResultCode.CANCELLED, null);

      appendErrorMessage(coe.getCancelRequest().getCancelReason());
    }
    finally
    {
      // Stop the processing timer.
      setProcessingStopTime();

      // Log the modify response.
      logModifyResponse(this);

      if(cancelRequest == null || cancelResult == null ||
          cancelResult.getResultCode() != ResultCode.CANCELLED ||
          cancelRequest.notifyOriginalRequestor() ||
          DirectoryServer.getCoreConfigManager().isNotifyAbandonedOperations())
      {
        clientConnection.sendResponse(this);
      }

      // Invoke the post-response callbacks.
      if (workflowExecuted) {
        invokePostResponseCallbacks();
      }

      // Invoke the post-response add plugins.
      invokePostResponsePlugins(workflowExecuted);

      // If no cancel result, set it
      if(cancelResult == null)
      {
        cancelResult = new CancelResult(ResultCode.TOO_LATE, null);
      }
    }
  }

  /**
   * Invokes the post response plugins. If a workflow has been executed
   * then invoke the post response plugins provided by the workflow
   * elements of the workflow, otherwise invoke the post response plugins
   * that have been registered with the current operation.
   *
   * @param workflowExecuted <code>true</code> if a workflow has been executed
   */
  private void invokePostResponsePlugins(boolean workflowExecuted)
  {
    // Invoke the post response plugins
    if (workflowExecuted)
    {
      // Invoke the post response plugins that have been registered by
      // the workflow elements
      @SuppressWarnings("unchecked")
      List<LocalBackendModifyOperation> localOperations =
          (List<LocalBackendModifyOperation>) getAttachment(
              Operation.LOCALBACKENDOPERATIONS);
      if (localOperations != null)
      {
        for (LocalBackendModifyOperation localOperation : localOperations)
        {
          getPluginConfigManager().invokePostResponseModifyPlugins(localOperation);
        }
      }
    }
    else
    {
      // Invoke the post response plugins that have been registered with
      // the current operation
      getPluginConfigManager().invokePostResponseModifyPlugins(this);
    }
  }

  @Override
  public void updateOperationErrMsgAndResCode()
  {
    setResultCode(ResultCode.NO_SUCH_OBJECT);
    appendErrorMessage(ERR_MODIFY_NO_SUCH_ENTRY.get(getEntryDN()));
  }


  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public Entry getCurrentEntry() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public List<ByteString> getCurrentPasswords()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public Entry getModifiedEntry()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * This method always returns null.
   */
  @Override
  public List<ByteString> getNewPasswords()
  {
    return null;
  }
}
