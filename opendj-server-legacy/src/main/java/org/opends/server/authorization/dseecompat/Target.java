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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2016 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import java.util.regex.Pattern;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPURL;

import static org.opends.messages.AccessControlMessages.*;
import static org.opends.server.authorization.dseecompat.Aci.*;

/**
 * A class representing an ACI target keyword.
 */
public class Target
{
    /**
     * Enumeration representing the target operator.
     */
    private EnumTargetOperator operator = EnumTargetOperator.EQUALITY;

    /**
     * True if the URL contained a DN wild-card pattern.
     */
    private boolean isPattern;

    /**
     * The target DN from the URL or null if it was a wild-card pattern.
     */
    private DN urlDN;

    /**
     * The pattern matcher for a wild-card pattern or null if the URL
     * contained an ordinary DN.
     */
    private PatternDN patternDN;

    /*
     * TODO Save aciDN parameter and use it in matchesPattern re-write.
     *
     * Should the aciDN argument provided to the constructor be stored so that
     * it can be used in the matchesPattern() method?  The DN should only be
     * considered a potential match if it is at or below the entry containing
     * the ACI.
     *
     */
    /**
     * This constructor parses the target string.
     * @param operator  An enumeration of the operation of this target.
     * @param target A string representation of the target.
     * @param aciDN The dn of the ACI entry used for a descendant check.
     * @throws AciException If the target string is invalid.
     */
    private Target(EnumTargetOperator operator, String target, DN aciDN)
            throws AciException {
        this.operator = operator;
        try {
          //The NULL_LDAP_URL corresponds to the root DSE.
          if (!NULL_LDAP_URL.equals(target) && !Pattern.matches(LDAP_URL, target)) {
              throw new AciException(WARN_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION.get(target));
          }
          LDAPURL targetURL =  LDAPURL.decode(target, false);
          if (targetURL.getRawBaseDN().contains("*")) {
              this.isPattern=true;
              patternDN = PatternDN.decodeSuffix(targetURL.getRawBaseDN());
          } else {
              urlDN=targetURL.getBaseDN();
              if(!urlDN.isSubordinateOrEqualTo(aciDN)) {
                  throw new AciException(WARN_ACI_SYNTAX_TARGET_DN_NOT_DESCENDENTOF.get(urlDN, aciDN));
              }
          }
        }
        catch (DirectoryException e){
            throw new AciException(WARN_ACI_SYNTAX_INVALID_TARGETKEYWORD_EXPRESSION.get(target));
        }
    }

    /**
     *  Decode an expression string representing a target keyword expression.
     * @param operator  An enumeration of the operation of this target.
     * @param expr A string representation of the target.
     * @param aciDN  The DN of the ACI entry used for a descendant check.
     * @return  A Target class representing this target.
     * @throws AciException  If the expression string is invalid.
     */
    public static Target decode(EnumTargetOperator operator,
                                String expr, DN aciDN)
            throws AciException {
        return new Target(operator, expr, aciDN);
    }

    /**
     * Returns the operator of this expression.
     * @return An enumeration of the operation value.
     */
    public EnumTargetOperator getOperator() {
        return operator;
    }

    /**
     * Returns the URL DN of the expression.
     * @return A DN of the URL or null if the URL contained a DN pattern.
     */
    public DN getDN() {
        return urlDN;
    }

    /**
     * Returns boolean if a pattern was seen during parsing.
     * @return  True if the URL contained a DN pattern.
     */
    public boolean isPattern() {
        return isPattern;
    }

    /**
     * This method tries to match a pattern against a DN.
     * @param dn  The DN to try an match.
     * @return True if the pattern matches.
     */
    public boolean matchesPattern(DN dn) {
        return patternDN.matchesDN(dn);
    }
}
