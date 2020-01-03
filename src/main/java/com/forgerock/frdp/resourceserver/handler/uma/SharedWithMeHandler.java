/*
 * Copyright (c) 2019-2020, ForgeRock, Inc., All rights reserved
 * Use subject to license terms.
 */

package com.forgerock.frdp.resourceserver.handler.uma;

import com.forgerock.frdp.common.ConstantsIF;
import com.forgerock.frdp.dao.Operation;
import com.forgerock.frdp.dao.OperationIF;
import com.forgerock.frdp.dao.mongo.MongoFactory;
import com.forgerock.frdp.handler.HandlerManagerIF;
import com.forgerock.frdp.resourceserver.ConfigIF;
import com.forgerock.frdp.resourceserver.dao.AMRestDataAccess;
import com.forgerock.frdp.resourceserver.handler.JaxrsHandler;
import com.forgerock.frdp.resourceserver.handler.JaxrsHandlerIF;
import com.forgerock.frdp.utils.JSON;
import com.forgerock.frdp.utils.STR;
import java.util.logging.Level;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Shared With Me Handler. Provides a service where the Requesting Party (RqP)
 * can find UMA resources that are currently "shared with them". This service is
 * NOT part of the UMA 2.0 specification. This class provides value-added
 * services which leverage the Access Manager APIs.
 * 
 * <pre>
 * This class implements the following operations:
 * - search: find all UMA resources that are shared to the given Requesting Party (RqP)
 * </pre>
 * 
 * @author Scott Fehrman, ForgeRock, Inc.
 */
public class SharedWithMeHandler extends JaxrsHandler {

   private final String CLASS = this.getClass().getName();

   /**
    * Constructor
    *
    * @param config     JSONObject containing configuration data
    * @param handlerMgr HandlerManagerIF provides management of Handlers
    */
   public SharedWithMeHandler(final JSONObject config, final HandlerManagerIF handlerMgr) {
      super(config, handlerMgr);

      String METHOD = "SharedWithMeHandler(config, handlerMgr)";

      _logger.entering(CLASS, METHOD);

      this.init();

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /*
    * ================= PROTECTED METHODS =================
    */

   /**
    * Override the "validate" interface, used to check the operation input
    * 
    * @param oper OperationaIF operation input
    * @exception Exception
    */
   @Override
   protected void validate(final OperationIF oper) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      JSONObject jsonInput = null;

      _logger.entering(CLASS, METHOD);

      if (oper == null) {
         throw new Exception("Operation object is null");
      }

      jsonInput = oper.getJSON();
      if (jsonInput == null || jsonInput.isEmpty()) {
         throw new Exception("JSON Input is null or empty");
      }

      switch (oper.getType()) {
      case SEARCH: // GET
      {
         break;
      }
      default: {
         throw new Exception("Unsupported operation type: '" + oper.getType().toString() + "'");
      }
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Override interface to support the "search" operation Get the collection of
    * shared resource for the Requesting Party (RqP)
    * 
    * <pre>
    * JSON input ... search for pending requests
    * { 
    *   "subject": "aadams", // Requesting Party (RqP) login id
    *   "sso_token": "..." // Requesting Party (RqP) SSO session token
    *   "query" : {
    *     "operator": "equal",
    *     "attribute": "",
    *     "value": ""
    *   }
    * }
    *
    * JSON output ...
    * {
    *   "data": {
    *     "results”:[
    *       {
    *         "id": "c1e0e9bd-371a-4021-aea6-52305a0e0f36", // resource id
    *         "owner": "bjensen",
    *         "name": "labRpt01",
    *         "type": "healthcare-report",
    *         "description": "Lab Report from Jan 2001",
    *         "resource_scopes": [ "view", "meta", "markup" ], // if discoverable
    *         "scopes": [ "view" ]
    *       }
    *     ],
    *     "quantity":1
    *   }
    * }
    * </pre>
    * 
    * @param operInput OperationIF input for search operation
    * @return OperationIF output from search operation
    */
   @Override
   protected OperationIF search(OperationIF operInput) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      OperationIF operOutput = null;

      _logger.entering(CLASS, METHOD);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL, "input=''{0}'', json=''{1}''",
               new Object[] { operInput != null ? operInput.toString() : NULL,
                     operInput.getJSON() != null ? operInput.getJSON().toString() : NULL });
      }

      try {
         operOutput = this.searchImpl(operInput);
      } catch (Exception ex) {
         operOutput = new Operation(OperationIF.TYPE.SEARCH);
         operOutput.setError(true);
         operOutput.setState(STATE.ERROR);
         operOutput.setStatus(ex.getMessage());
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /*
    * =============== PRIVATE METHODS ===============
    */

   /**
    * Initialize object instance
    */
   private void init() {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();

      _logger.entering(CLASS, METHOD);

      /*
       * setup the Mongo Data Access Object
       */
      if (_MongoDAO == null) {
         try {
            _MongoDAO = MongoFactory.getInstance(JSON.convertToParams(JSON.getObject(_config, ConfigIF.RS_NOSQL)));
         } catch (Exception ex) {
            this.setError(true);
            this.setState(STATE.ERROR);
            this.setStatus(CLASS + ": " + METHOD + ": Mongo DAO:" + ex.getMessage());
            _logger.severe(this.getStatus());
         }
      }

      if (!this.isError() && _AuthzServerDAO == null) {
         try {
            _AuthzServerDAO = new AMRestDataAccess(JSON.convertToParams(JSON.getObject(_config, ConfigIF.AS_CONNECT)));
         } catch (Exception ex) {
            this.setError(true);
            this.setState(STATE.ERROR);
            this.setStatus(CLASS + ": " + METHOD + ": REST DAO: " + ex.getMessage());
            _logger.severe(this.getStatus());
         }
      }

      if (!this.isError()) {
         this.setState(STATE.READY);
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Implementation of the "search" operation. Get the "shared" resource for the
    * Requesting Party (RqP)
    *
    * <pre>
    * Get the resources that are "shared with me" (the RqP)
    * - Get AM "admin" credentials (sso token), needed for AM Policy API
    * - Call AM REST interface to get a list of UMA "registrations" that are
    *   shared with the subject (RqP)
    * - For each registration:
    *   - Call AM REST interface to get the related Policy data
    *     Get the scopes associated with the subject (RqP)
    *   - Use the "registration id" to get the resource data
    *     Get resource attributes: description
    *   - Create JSON Object for output result
    *
    * JSON input ... search for pending requests
    * { 
    *   "subject": "aadams", // Requesting Party (RqP) login id
    *   "sso_token": "..." // Requesting Party (RqP) SSO session token
    *   "query" : { // OPTIONAL ... used to filter the results
    *     "operator": "equal",
    *     "attribute": "type",
    *     "value": "healthcare"
    *   }
    * }
    *
    * AM API output ...
    * {
    *   "result": [
    *     {
    *       "_id": "f25424b6-dd67-4403-91db-ecba96b3365b0",
    *       "_rev": "1364060076",
    *       "resourceServer": "UMA-Resource-Server",
    *       "name": "Patient001",
    *       "resourceOwnerId": "bjensen",
    *       "scopes": ["view","meta"],
    *       "type": "healthcare-fhir-patient",
    *       "icon_uri": "https://.../shared/icons/emblem-money.png",
    *       "labels": []
    *     },
    *     { ...}
    *   ],
    *   "resultCount": 8,
    *   "pagedResultsCookie": null,
    *   "totalPagedResultsPolicy": "NONE",
    *   "totalPagedResults": -1,
    *   "remainingPagedResults": 0
    * }
    *
    * JSON output ...
    * {
    *   "data": {
    *     "results”:[
    *       {
    *         "id": "c1e0e9bd-371a-4021-aea6-52305a0e0f36",
    *         "owner": "bjensen",
    *         "name": "labRpt01",
    *         "type": "healthcare/report",
    *         "description": "Lab Report from Jan 2001",
    *         "resource_scopes": [ "view", "meta", "markup" ], // if discoverable
    *         "scopes": [ "view" ]
    *       }
    *     ],
    *     "quantity":1
    *   }
    * }
    *
    * Use AM oauth2/resources/sets API to get the resource that are 
    * "shared with me" ... the Requesting Party (RqP)
    *
    * The response is a collection of Resource objects that the RqP
    * has "some" policy to give them access.  The "scopes" in a response 
    * record is the list of possible scopes for the resource ... not the
    * scopes that the RqP currently has.
    *
    * curl example:
    * curl -X GET
    * -H 'Accept-API-Version: protocol=1.0,resource=1.0’ 
    * -H "iPlanetDirectoryPro: {{sso_token}}"
    * https://.../openam/json/realms/root/users/{{subject}}/oauth2/resources/sets
    * ?_sortKeys=name
    * &_queryFilter=!%20resourceOwnerId%20eq%20%22{{subject}}%22
    *
    * JSON output ...
    * {
    *   "result": [
    *     {
    *       "_id": "f25424b6-dd67-4403-91db-ecba96b3365b0",
    *       "_rev": "1364060076",
    *       "resourceServer": "UMA-Resource-Server",
    *       "name": "Patient001",
    *       "resourceOwnerId": "bjensen",
    *       "scopes": ["view","meta"],
    *       "type": "healthcare-fhir-patient",
    *       "icon_uri": "https://.../shared/icons/emblem-money.png",
    *       "labels": []
    *     },
    *     { ...}
    *   ],
    *   "resultCount": 8,
    *   "pagedResultsCookie": null,
    *   "totalPagedResultsPolicy": "NONE",
    *   "totalPagedResults": -1,
    *   "remainingPagedResults": 0
    * }
    *
    * </pre>
    * 
    * @param operInput OperationIF input
    * @return OperationIF output
    * @throws Exception
    */
   private OperationIF searchImpl(final OperationIF operInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String subject = null; // Requesting Party
      String sso_token = null;
      JSONObject jsonInput = null;
      JSONObject jsonQuery = null;
      JSONObject jsonHeaders = null;
      JSONObject jsonParams = null;
      JSONObject jsonData = null;
      JSONObject jsonSearch = null;
      JSONObject jsonOutput = null;
      OperationIF operOutput = null;
      OperationIF operASInput = null;
      OperationIF operASOutput = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());

      jsonInput = operInput.getJSON();

      subject = JSON.getString(jsonInput, ConstantsIF.SUBJECT); // Requesting Party
      sso_token = JSON.getString(jsonInput, ConstantsIF.SSO_TOKEN);

      if (!STR.isEmpty(subject)) {
         jsonQuery = new JSONObject(); // SEARCH Operations require a "query" object
         jsonQuery.put(ConstantsIF.OPERATOR, ConstantsIF.NONE);

         jsonHeaders = new JSONObject();
         jsonHeaders.put(ConstantsIF.ACCEPT_API_VERSION, this.getConfigValue(ConfigIF.AS_UMA_SHAREDWITHME_ACCEPT));
         jsonHeaders.put(this.getConfigValue(ConfigIF.AS_COOKIE), sso_token);

         jsonParams = new JSONObject();
         jsonParams.put(PROP_SORTKEYS, this.getConfigValue(ConfigIF.AS_UMA_SHAREDWITHME_SORTKEYS));
         jsonParams.put(PROP_QUERYFILTER,
               this.getConfigValue(ConfigIF.AS_UMA_SHAREDWITHME_QUERYFILTER).replaceAll(PROP_VAR_OWNER, subject));

         jsonSearch = new JSONObject();
         jsonSearch.put(ConstantsIF.QUERY, jsonQuery);
         jsonSearch.put(ConstantsIF.HEADERS, jsonHeaders);
         jsonSearch.put(ConstantsIF.QUERY_PARAMS, jsonParams);
         jsonSearch.put(ConstantsIF.PATH,
               this.getConfigValue(ConfigIF.AS_UMA_SHAREDWITHME_PATH).replaceAll(PROP_VAR_OWNER, subject));

         operASInput = new Operation(OperationIF.TYPE.SEARCH); // GET
         operASInput.setJSON(jsonSearch);

         operASOutput = _AuthzServerDAO.execute(operASInput);

         operOutput.setState(operASOutput.getState());
         operOutput.setStatus(operASOutput.getStatus());

         if (operASOutput.getState() == STATE.SUCCESS) {
            jsonData = operASOutput.getJSON();
         } else {
            jsonData = new JSONObject();
            jsonData.put(ConstantsIF.RESULTS, new JSONArray());
         }

         jsonData = this.filter(jsonData, JSON.getObject(jsonInput, ConstantsIF.QUERY));
         jsonData.put(ConstantsIF.SUBJECT, subject);

         jsonData = this.updateResourceData(jsonData);

         jsonOutput = new JSONObject();
         jsonOutput.put(ConstantsIF.DATA, jsonData);
         operOutput.setJSON(jsonOutput);
      } else {
         throw new Exception(METHOD + ": owner is empty");
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Update the resource data. Get an "admin" SSO token, needed to read the policy
    * Update each "entry" with Resource data Need to use the "registerId" (from AM)
    * to get the Resource record NOTE: input array is named "result" (from AM AI)
    * output array is named "results" to match other RS search responses Get the
    * policy for each registration and find the "assigned" scopes
    * 
    * <pre>
    * JSON input ...
    * {
    *   "subject": "aadams", // Requesting Party
    *   "result": [
    *     {
    *       "_id": "f25424b6-dd67-4403-91db-ecba96b3365b0", // AM REGISTER ID (DELETE)
    *       "_rev": "1364060076", // (DELETE)
    *       "resourceServer": "UMA-Resource-Server", // (DELETE)
    *       "name": "Patient001",
    *       "resourceOwnerId": "bjensen", // (RENAME) = "owner"
    *       "scopes": ["view","meta"],
    *       "type": "healthcare-fhir-patient",
    *       "icon_uri": "https://.../shared/icons/emblem-money.png",
    *       "labels": [] // (DELETE)
    *     },
    *     { ...}
    *   ],
    *   "resultCount": 8,
    *   "pagedResultsCookie": null,
    *   "totalPagedResultsPolicy": "NONE",
    *   "totalPagedResults": -1,
    *   "remainingPagedResults": 0
    * }
    *
    * JSON output ...
    * {
    *   "results": [
    *     {
    *       "id": "a4cd56f3-8320-4f66-a21a-96af75433d08", // CS RESOURCE ID (NEW)
    *       "name": "Patient001",
    *       "owner": "bjensen",
    *       "scopes": ["view","meta"], // POSSIBLE SCOPES, IF DISCOVERABLE (REMOVE)
    *       "policy": ["view"], // SCOPES ALLOWED PER POLICY
    *       "type": "healthcare-fhir-patient",
    *       "icon_uri": "https://.../shared/icons/emblem-money.png",
    *       "label": "Patient 001", // (NEW)
    *       "description": "FHIR Patient Record" // (NEW)
    *     },
    *     { ...}
    *   ],
    *   "quantity": 8
    * }
    * Get a sso session token with "admin" credentials
    * {
    *   "data": {
    *     "tokenId": "...*...*",
    *     "successUrl": "/openam/console",
    *     "realm": "/"
    *   }
    * }
    *
    * search the resources for a given registration id:
    * JSON input ...
    * {
    *   "query": {
    *     "operator": "equal",
    *     "attribute": "data.register",
    *     "value": "f25424b6-dd67-4403-91db-ecba96b3365b0"
    *   }
    * }
    * JSON output ...
    * {
    *   "data": {
    *     "quantity": x,
    *     "results": [
    *       {
    *         "uid": "a4cd56f3-8320-4f66-a21a-96af75433d08" // resource id
    *         "data" : {
    *           "register": "f25424b6-dd67-4403-91db-ecba96b3365b0",
    *           "meta": {
    *             "label": "Patient 001",
    *             "description": "FHIR Patient Record",
    *           },
    *         }
    *       },
    *     ]
    *   }
    * }
    *
    * </pre>
    * 
    * @param jsonInput JSONObject input
    * @return JSONObject output
    * @throws Exception
    */
   private JSONObject updateResourceData(final JSONObject jsonInput) throws Exception {
      boolean discoverable = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String registerId = null;
      String rsId = null;
      String sso_token = null;
      String resourceServer = null;
      JSONObject jsonOutput = null;
      JSONObject jsonSearch = null;
      JSONObject jsonQuery = null;
      JSONObject jsonRegister = null;
      JSONObject jsonResource = null;
      JSONObject jsonPolicy = null;
      JSONArray arrayInput = null;
      JSONArray arrayOutput = null;
      JSONArray arrayPolicyScopes = null;
      OperationIF operInput = null;
      OperationIF operOutput = null;
      OperationIF operProxyInput = null;
      OperationIF operProxyOutput = null;
      JaxrsHandlerIF resourceHandler = null;
      JaxrsHandlerIF proxyAdmHandler = null;

      _logger.entering(CLASS, METHOD);

      resourceHandler = this.getHandler(JaxrsHandlerIF.HANDLER_RESOURCES);

      proxyAdmHandler = this.getHandler(JaxrsHandlerIF.HANDLER_AMPROXYADM);

      rsId = this.getConfigValue(ConfigIF.RS_ID);

      operProxyInput = new Operation(OperationIF.TYPE.READ);
      operProxyOutput = proxyAdmHandler.process(operProxyInput);

      sso_token = JSON.getString(operProxyOutput.getJSON(), ConstantsIF.DATA + "." + ConstantsIF.TOKENID);

      if (STR.isEmpty(sso_token)) {
         this.abort(METHOD, "Proxy Admin sso token is empty");
      }

      /*
       * process the input
       */
      jsonOutput = new JSONObject();
      arrayOutput = new JSONArray();

      if (jsonInput != null) {
         arrayInput = JSON.getArray(jsonInput, ConstantsIF.RESULT);

         if (arrayInput != null && !arrayInput.isEmpty()) {
            operInput = new Operation(OperationIF.TYPE.SEARCH);

            jsonQuery = new JSONObject();
            jsonQuery.put(ConstantsIF.OPERATOR, ConstantsIF.EQUAL);
            jsonQuery.put(ConstantsIF.ATTRIBUTE, ConstantsIF.DATA + "." + ConstantsIF.REGISTER);
            // Need to set the value for each entry

            jsonSearch = new JSONObject();
            jsonSearch.put(ConstantsIF.QUERY, jsonQuery);

            for (Object obj : arrayInput) {
               if (obj != null && obj instanceof JSONObject) {
                  jsonRegister = (JSONObject) obj;
                  registerId = JSON.getString(jsonRegister, ConstantsIF._ID);

                  if (!STR.isEmpty(registerId)) {
                     resourceServer = JSON.getString(jsonRegister, JaxrsHandler.AM_ATTR_RESOURCE_SERVER);

                     /*
                      * The "subject" could have "shared with me" resources that come from different
                      * Resource Servers We only want the ones related to "this" RS The
                      * "Resource Server" value MUST match the Resource Server Id (rsId)
                      */
                     if (!STR.isEmpty(resourceServer) && resourceServer.equalsIgnoreCase(rsId)) {

                        jsonQuery.put(ConstantsIF.VALUE, registerId);

                        operInput.setJSON(jsonSearch);

                        operOutput = resourceHandler.process(operInput);

                        if (operOutput != null && operOutput.getState() == STATE.SUCCESS) {
                           jsonResource = JSON.getObject(operOutput.getJSON(),
                                 ConstantsIF.DATA + "." + ConstantsIF.RESULTS + "[0]");

                           if (jsonResource != null && !jsonResource.isEmpty()) {
                              /*
                               * Get the the subject's current scopes, from policy
                               */
                              jsonPolicy = new JSONObject();
                              jsonPolicy.put(ConstantsIF.SSO_TOKEN, sso_token);
                              jsonPolicy.put(ConstantsIF.SUBJECT, JSON.getString(jsonInput, ConstantsIF.SUBJECT));
                              jsonPolicy.put(ConstantsIF.REGISTERED, registerId);
                              jsonPolicy.put(ConstantsIF.OWNER,
                                    JSON.getString(jsonRegister, JaxrsHandler.AM_ATTR_RESOURCE_OWNER_ID));

                              arrayPolicyScopes = this.getScopes(jsonPolicy);
                              if (arrayPolicyScopes != null) {
                                 jsonRegister.put(ConstantsIF.POLICY, arrayPolicyScopes);
                              }

                              jsonRegister.put(ConstantsIF.ID, JSON.getString(jsonResource, ConstantsIF.UID));
                              jsonRegister.put(ConstantsIF.LABEL, JSON.getString(jsonResource,
                                    ConstantsIF.DATA + "." + ConstantsIF.META + "." + ConstantsIF.LABEL));
                              jsonRegister.put(ConstantsIF.DESCRIPTION, JSON.getString(jsonResource,
                                    ConstantsIF.DATA + "." + ConstantsIF.META + "." + ConstantsIF.DESCRIPTION));
                              jsonRegister.put(ConstantsIF.OWNER,
                                    jsonRegister.get(JaxrsHandler.AM_ATTR_RESOURCE_OWNER_ID));

                              jsonRegister.remove(ConstantsIF._ID);
                              jsonRegister.remove(ConstantsIF._REV);
                              jsonRegister.remove(JaxrsHandler.AM_ATTR_RESOURCE_SERVER);
                              jsonRegister.remove(ConstantsIF.LABELS);
                              jsonRegister.remove(JaxrsHandler.AM_ATTR_RESOURCE_OWNER_ID);

                              discoverable = JSON.getBoolean(jsonResource,
                                    ConstantsIF.DATA + "." + ConstantsIF.META + "." + ConstantsIF.DISCOVERABLE);

                              if (!discoverable) {
                                 jsonRegister.remove(ConstantsIF.SCOPES);
                              }

                              arrayOutput.add(jsonRegister);
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      jsonOutput.put(ConstantsIF.RESULTS, arrayOutput);
      jsonOutput.put(ConstantsIF.QUANTITY, arrayOutput.size());

      _logger.exiting(CLASS, METHOD);

      return jsonOutput;
   }

   /**
    * If a filter is provided ... Remove the matching objects from the "result"
    * array
    *
    * <pre>
    * JSON input ...
    * {
    *   "result": [
    *     {
    *       "_id": "f25424b6-dd67-4403-91db-ecba96b3365b0",
    *       "_rev": "1364060076",
    *       "resourceServer": "UMA-Resource-Server",
    *       "name": "Patient001",
    *       "resourceOwnerId": "bjensen",
    *       "scopes": ["view","meta"],
    *       "type": "healthcare-fhir-patient",
    *       "icon_uri": "https://.../shared/icons/emblem-money.png",
    *       "labels": []
    *     },
    *     { ...}
    *   ],
    *   "resultCount": 8,
    *   "pagedResultsCookie": null,
    *   "totalPagedResultsPolicy": "NONE",
    *   "totalPagedResults": -1,
    *   "remainingPagedResults": 0
    * }
    *
    * Filter ...
    * { 
    *   "operator": "equals",
    *   "attribute": "type",
    *   "value": "healthcare"
    * }
    * </pre>
    * 
    * @param jsonInput  JSONObject input
    * @param jsonFilter JSONObject filter
    * @return JSONObject output
    */
   private JSONObject filter(final JSONObject jsonInput, final JSONObject jsonFilter) {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String filterAttr = null;
      String filterVal = null;
      String attrVal = null;
      JSONObject jsonOutput = null;
      JSONObject jsonResult = null;
      JSONArray arrayResultsInput = null;
      JSONArray arrayResultsOutput = null;

      _logger.entering(CLASS, METHOD);

      if (jsonFilter != null) {
         jsonOutput = new JSONObject();
         arrayResultsOutput = new JSONArray();

         filterAttr = JSON.getString(jsonFilter, ConstantsIF.ATTRIBUTE);
         filterVal = JSON.getString(jsonFilter, ConstantsIF.VALUE);

         if (!STR.isEmpty(filterAttr) && !STR.isEmpty(filterVal)) {
            arrayResultsInput = JSON.getArray(jsonInput, ConstantsIF.RESULT);
            if (arrayResultsInput != null && !arrayResultsInput.isEmpty()) {
               for (Object obj : arrayResultsInput) {
                  if (obj != null && obj instanceof JSONObject) {
                     jsonResult = (JSONObject) obj;
                     attrVal = JSON.getString(jsonResult, filterAttr);

                     if (!STR.isEmpty(attrVal) && attrVal.equalsIgnoreCase(filterVal)) {
                        arrayResultsOutput.add(jsonResult);
                     }
                  }
               }
            }
         }
         jsonOutput.put(ConstantsIF.RESULT, arrayResultsOutput);
         jsonOutput.put("resultCount", arrayResultsOutput.size());
      } else {
         jsonOutput = jsonInput;
      }

      _logger.exiting(CLASS, METHOD);

      return jsonOutput;
   }

   /**
    * Get the scopes, for a registered resource, to an owner, for a specific
    * subject (Requesting Party)
    * 
    * <pre>
    * JSON input ...
    * {
    *   "ssotoken": "...", // admin session sso token
    *   "registerId": "...",
    *   "owner": "...",
    *   "subject": "..."
    * }
    * JSON output ...
    * [ "view" ]
    * 
    * curl example:
    * curl -X GET \
    * -H "iPlanetDirectoryPro: <<ssoToken>>" \
    * https://.../openam/json/realms/root/users/<<owner>>/uma/policies/<<regId>>
    *
    * {
    *   "_id": "0d7790de-9066-4bb6-8e81-25b6f9d0b8853",
    *   "_rev": "1444644662",
    *   "policyId": "0d7790de-9066-4bb6-8e81-25b6f9d0b8853",
    *   "name": "Photo Album",
    *   "permissions": [
    *     {
    *       "subject": "bob",
    *       "scopes": ["view", "comment"]
    *     }
    *   ]
    * }
    * </pre>
    * 
    * @param jsonInput JSONObject input
    * @return JOSNObject output
    * @throws Exception
    */
   private JSONArray getScopes(final JSONObject jsonInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String registerId = null;
      String owner = null; // Resource Owner
      String subject = null; // Requesting Party
      String sso_token = null;
      String permSub = null;
      JSONObject jsonHeaders = null;
      JSONObject jsonData = null;
      JSONObject jsonPolicy = null;
      JSONObject jsonPermission = null;
      JSONArray arrayPermissions = null;
      JSONArray arrayScopes = null;
      OperationIF operInput = null;
      OperationIF operOutput = null;

      _logger.entering(CLASS, METHOD);

      if (jsonInput == null || jsonInput.isEmpty()) {
         this.abort(METHOD, "JSON input is null or empty");
      }

      sso_token = JSON.getString(jsonInput, ConstantsIF.SSO_TOKEN);
      registerId = JSON.getString(jsonInput, ConstantsIF.REGISTERED);
      owner = JSON.getString(jsonInput, ConstantsIF.OWNER);
      subject = JSON.getString(jsonInput, ConstantsIF.SUBJECT);

      if (STR.isEmpty(sso_token) || STR.isEmpty(registerId) || STR.isEmpty(owner) || STR.isEmpty(subject)) {
         this.abort(METHOD, "One or more of the input attributes is empty");
      }

      jsonHeaders = new JSONObject();
      jsonHeaders.put(this.getConfigValue(ConfigIF.AS_COOKIE), sso_token);

      jsonData = new JSONObject();
      jsonData.put(ConstantsIF.HEADERS, jsonHeaders);
      jsonData.put(ConstantsIF.UID, registerId);
      jsonData.put(ConstantsIF.PATH,
            this.getConfigValue(ConfigIF.AS_UMA_POLICIES_PATH).replaceAll(PROP_VAR_OWNER, owner));

      operInput = new Operation(OperationIF.TYPE.READ); // GET
      operInput.setJSON(jsonData);

      operOutput = _AuthzServerDAO.execute(operInput);

      if (operOutput.getState() == STATE.SUCCESS) {
         jsonPolicy = operOutput.getJSON();

         if (jsonPolicy == null || jsonPolicy.isEmpty()) {
            this.abort(METHOD, "JSON output is empty: " + operOutput.getStatus());
         }

         arrayPermissions = JSON.getArray(jsonPolicy, ConstantsIF.PERMISSIONS);

         if (arrayPermissions != null && !arrayPermissions.isEmpty()) {
            for (Object obj : arrayPermissions) {
               if (obj != null && obj instanceof JSONObject) {
                  jsonPermission = (JSONObject) obj;
                  permSub = JSON.getString(jsonPermission, ConstantsIF.SUBJECT);
                  if (!STR.isEmpty(permSub) && permSub.equalsIgnoreCase(subject)) {
                     arrayScopes = JSON.getArray(jsonPermission, ConstantsIF.SCOPES);
                  }
               }
            }
         }
      } else {
         _logger.log(Level.WARNING, "{0}: Could not read resource policy: {1}",
               new Object[] { METHOD, operOutput.getStatus() });
      }

      _logger.exiting(CLASS, METHOD);

      return arrayScopes;
   }
}
