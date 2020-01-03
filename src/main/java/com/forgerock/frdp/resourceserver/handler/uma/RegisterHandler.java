/*
 * Copyright (c) 2018-2020, ForgeRock, Inc., All rights reserved
 * Use subject to license terms.
 */

package com.forgerock.frdp.resourceserver.handler.uma;

import com.forgerock.frdp.common.ConstantsIF;
import com.forgerock.frdp.dao.Operation;
import com.forgerock.frdp.dao.OperationIF;
import com.forgerock.frdp.handler.HandlerManagerIF;
import com.forgerock.frdp.resourceserver.ConfigIF;
import com.forgerock.frdp.resourceserver.dao.AMRestDataAccess;
import com.forgerock.frdp.resourceserver.handler.JaxrsHandler;
import com.forgerock.frdp.utils.JSON;
import com.forgerock.frdp.utils.STR;
import java.util.logging.Level;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Register Handler. Provides UMA resource registration operations.
 * 
 * <pre>
 * This class implements the following operations:
 * - create: regsiter a new UMA resource, returns registeration id
 * - search: find registered UMA resources
 * - read: read UMA resource registration data (meta data)
 * - replace: update the UMA resource registration data (meta data)
 * - delete: de-register the UMA resource
 * </pre>
 * 
 * @author Scott Fehrman, ForgeRock, Inc.
 */
public class RegisterHandler extends JaxrsHandler {

   private final String CLASS = this.getClass().getName();
   private String _path = null;

   /**
    * Constructor
    *
    * @param config     JSONObject containing configuration data
    * @param handlerMgr HandlerManagerIF provides management of Handlers
    */
   public RegisterHandler(final JSONObject config, final HandlerManagerIF handlerMgr) {
      super(config, handlerMgr);

      String METHOD = "ContentHandler(config, handlerMgr)";

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
      case READ:
      case REPLACE:
      case DELETE: {
         this.checkUid(jsonInput);
         break;
      }
      default:
         break;
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Override interface to support the "create" operation
    * 
    * <pre>
    * 
    * JSON input ...
    * {
    *   "data": {
    *     "name": "", 
    *     "type": "",
    *     "resource_scopes": [ "view", ...  ]
    *   },
    *   "access_token": "..."
    * }
    * JSON output ...
    * { "uid": "..." }
    * </pre>
    * 
    * @param operInput OperationIF input for create operation
    * @return OperationIF output from create operation
    */
   @Override
   protected OperationIF create(final OperationIF operInput) {
      boolean error = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String msg = null;
      String registerId = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;

      _logger.entering(CLASS, METHOD);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL, "input=''{0}'', json=''{1}''",
               new Object[] { operInput != null ? operInput.toString() : NULL,
                     operInput.getJSON() != null ? operInput.getJSON().toString() : NULL });
      }

      operOutput = new Operation(operInput.getType());

      jsonInput = operInput.getJSON();

      try {
         registerId = this.createImpl(jsonInput);
      } catch (Exception ex) {
         error = true;
         msg = ex.getMessage();
      }

      if (!error) {
         if (!STR.isEmpty(registerId)) {
            jsonOutput = new JSONObject();
            jsonOutput.put(ConstantsIF.UID, registerId);

            operOutput.setJSON(jsonOutput);
            operOutput.setState(STATE.SUCCESS);
         } else {
            error = true;
            msg = "regsiterId is empty";
         }
      }

      if (error) {
         operOutput.setError(true);
         operOutput.setState(STATE.ERROR);
         operOutput.setStatus(METHOD + ": " + msg);
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Override interface to support the "search" operation
    * 
    * <pre>
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

   /**
    * Override interface to support the "read" operation
    * 
    * <pre>
    * JSON input ... read registration
    * { 
    *   "uid": "...", // Register Id
    *   "access_token": "..."
    * }
    * JSON output ...
    * { "data": { ... } }
    * </pre>
    * 
    * @param operInput OperationIF input for read operation
    * @return OperationIF output from read operation
    */
   @Override
   protected OperationIF read(OperationIF operInput) {
      boolean error = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String msg = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;
      JSONObject jsonOutput = null;
      JSONObject jsonWrapData = null;

      _logger.entering(CLASS, METHOD);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL, "input=''{0}'', json=''{1}''",
               new Object[] { operInput != null ? operInput.toString() : NULL,
                     operInput.getJSON() != null ? operInput.getJSON().toString() : NULL });
      }

      operOutput = new Operation(OperationIF.TYPE.READ);

      jsonInput = operInput.getJSON();

      try {
         jsonOutput = this.readImpl(jsonInput);
      } catch (Exception ex) {
         error = true;
         msg = ex.getMessage();
      }

      if (!error) {
         if (jsonOutput == null) {
            operOutput.setState(STATE.NOTEXIST);
            operOutput.setStatus("Missing entry");
         } else {
            jsonWrapData = new JSONObject();
            jsonWrapData.put(ConstantsIF.DATA, jsonOutput);

            operOutput.setJSON(jsonWrapData);
            operOutput.setState(STATE.SUCCESS);
            operOutput.setStatus("Found entry");
         }
      } else {
         operOutput.setError(true);
         operOutput.setState(STATE.ERROR);
         operOutput.setStatus(msg);
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Override interface to support the "replace" operation
    *
    * <pre>
    * JSON input ... replace registration
    * {
    *   "data": {
    *     "name": "", 
    *     "type": "",
    *     "resource_scopes": [ "view", ...  ]
    *   },
    *   "access_token": "...",
    *   "uid": "..."
    * }
    * </pre>
    * 
    * @param operInput OperationIF input for replace operation
    * @return OperationIF output from replace operation
    */
   @Override
   protected OperationIF replace(OperationIF operInput) {
      boolean error = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String msg = null;
      OperationIF operOutput = null;
      JSONObject jsonInput = null;

      _logger.entering(CLASS, METHOD);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL, "input=''{0}'', json=''{1}''",
               new Object[] { operInput != null ? operInput.toString() : NULL,
                     operInput.getJSON() != null ? operInput.getJSON().toString() : NULL });
      }

      operOutput = new Operation(OperationIF.TYPE.REPLACE);

      jsonInput = operInput.getJSON();

      try {
         this.replaceImpl(jsonInput);
      } catch (Exception ex) {
         error = true;
         msg = ex.getMessage();
      }

      if (!error) {
         operOutput.setJSON(new JSONObject());
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Replaced entry");
      } else {
         operOutput.setError(true);
         operOutput.setState(STATE.ERROR);
         operOutput.setStatus(msg);
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Override interface to support the "delete" operation
    *
    * <pre>
    * JSON input ... delete registration
    * { 
    *   "uid": "...",
    *   "access_token": "..."
    * }
    * </pre>
    * 
    * @param operInput OperationIF input for delete operation
    * @return OperationIF output from delete operation
    */
   @Override
   protected OperationIF delete(OperationIF operInput) {
      boolean error = false;
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String msg = null;
      JSONObject jsonInput = null;
      OperationIF operOutput = null;

      _logger.entering(CLASS, METHOD);

      if (_logger.isLoggable(DEBUG_LEVEL)) {
         _logger.log(DEBUG_LEVEL, "input=''{0}'', json=''{1}''",
               new Object[] { operInput != null ? operInput.toString() : NULL,
                     operInput.getJSON() != null ? operInput.getJSON().toString() : NULL });
      }

      operOutput = new Operation(OperationIF.TYPE.DELETE);

      jsonInput = operInput.getJSON();

      try {
         this.deleteImpl(jsonInput);
      } catch (Exception ex) {
         error = true;
         msg = ex.getMessage();
      }

      if (!error) {
         operOutput.setJSON(new JSONObject());
         operOutput.setState(STATE.SUCCESS);
         operOutput.setStatus("Deleted entry");
      } else {
         operOutput.setError(true);
         operOutput.setState(STATE.ERROR);
         operOutput.setStatus(msg);
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
       * setup the REST Data Access Object for the Authorization Server (AS)
       */

      if (_AuthzServerDAO == null) {
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
         try {
            _path = this.getConfigValue(ConfigIF.AS_UMA_RESOURCE_SET_PATH);
         } catch (Exception ex) {
            this.setError(true);
            this.setState(STATE.ERROR);
            this.setStatus(CLASS + ": " + METHOD + ": _path : " + ex.getMessage());
            _logger.log(Level.SEVERE, this.getStatus());
         }
      }

      if (!this.isError()) {
         this.setState(STATE.READY);
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Implementation of the "create" operation.
    * 
    * <pre>
    * JSON input ...
    * {
    *   "data": {
    *     "name": "", 
    *     "type": "",
    *     "resource_scopes": [ "view", ...  ]
    *   },
    *   "access_token": "..."
    * }
    * String output ... "_id" (uid) for AM UMA Resource
    *
    * curl example:
    * curl -request POST \
    * --header "Content-Type: application/json" \
    * --header "Authorization: Bearer <<uma_pat>>" \
    * --data \
    *       '{
    *          "name" : "Photo Album",
    *          "resource_scopes" : [ "view", ... ],
    *          "type" : "medical/report"
    *        }' \
    * https://.../openam/uma/realms/root/resource_set
    * {
    *    "_id": "126615ba-b7fd-4660-b281-bae81aa45f7c0",
    *    "user_access_policy_uri": "https://.../XUI/?realm=/#uma/share/..."
    * }
    * </pre>
    * 
    * @param jsonInput JSONObject input
    * @return String registration unique identifier
    * @throws Exception
    */
   private String createImpl(final JSONObject jsonInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String uid = null;
      String access_token = null;
      OperationIF operInput = null;
      OperationIF operOutput = null;
      JSONObject jsonRegisterHeaders = null;
      JSONObject jsonRegisterData = null;
      JSONObject jsonRegisterInput = null;
      JSONObject jsonOutput = null;

      _logger.entering(CLASS, METHOD);

      access_token = JSON.getString(jsonInput, ConstantsIF.ACCESS_TOKEN);

      if (STR.isEmpty(access_token)) {
         throw new Exception(METHOD + ": access_token is empty");
      }

      jsonRegisterData = JSON.getObject(jsonInput, ConstantsIF.DATA);

      if (jsonRegisterData == null || jsonRegisterData.isEmpty()) {
         throw new Exception(METHOD + ": JSON 'data' is empty");
      }

      jsonRegisterHeaders = new JSONObject();
      jsonRegisterHeaders.put(ConstantsIF.CONTENT_TYPE, ConstantsIF.APPLICATION_JSON);
      jsonRegisterHeaders.put(ConstantsIF.AUTHORIZATION, "Bearer " + access_token);

      jsonRegisterInput = new JSONObject();
      jsonRegisterInput.put(ConstantsIF.HEADERS, jsonRegisterHeaders);
      jsonRegisterInput.put(ConstantsIF.DATA, jsonRegisterData);
      jsonRegisterInput.put(ConstantsIF.PATH, _path);

      operInput = new Operation(OperationIF.TYPE.CREATE);
      operInput.setJSON(jsonRegisterInput);

      operOutput = _AuthzServerDAO.execute(operInput);

      if (operOutput.getState() == STATE.SUCCESS) {
         jsonOutput = operOutput.getJSON();

         uid = JSON.getString(jsonOutput, ConstantsIF.UID);
      } else {
         throw new Exception(METHOD + ": " + operOutput.getStatus());
      }

      _logger.exiting(CLASS, METHOD);

      return uid;
   }

   /**
    * Implementation of the "search" operation.
    * 
    * <pre>
    * JSON input ...
    * {
    *   "query": {
    *     "operator": "eq",
    *     "attribute": "access_token",
    *     "value": "..."
    *   }
    * }
    * JSON output ...
    * {
    *   "results": [
    *     { ... },
    *     ...
    *   ]
    * }
    *
    * curl example:
    * Get the "registered" resources for the owner (using the PAT)
    * curl -X GET\
    * --header "Authorization: Bearer 515d6551-6512-5279-98b6-c0ef3f03a723" \
    * https://...:8443/openam/uma/realms/root/resource_set
    * </pre>
    * 
    * @param operInput OperationIF input
    * @return OperationIF output
    * @throws Exception
    */
   private OperationIF searchImpl(final OperationIF operInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String access_token = null;
      JSONObject jsonOutput = null;
      JSONObject jsonHeaders = null;
      JSONObject jsonSearch = null;
      OperationIF operOutput = null;
      OperationIF operASInput = null;
      OperationIF operASOutput = null;

      _logger.entering(CLASS, METHOD);

      operOutput = new Operation(operInput.getType());

      access_token = JSON.getString(operInput.getJSON(), ConstantsIF.QUERY + "." + ConstantsIF.VALUE);

      if (!STR.isEmpty(access_token)) {
         jsonHeaders = new JSONObject();
         jsonHeaders.put(ConstantsIF.AUTHORIZATION, "Bearer " + access_token);

         jsonSearch = new JSONObject();
         jsonSearch.put(ConstantsIF.HEADERS, jsonHeaders);
         jsonSearch.put(ConstantsIF.PATH, _path);

         operASInput = new Operation(OperationIF.TYPE.READ); // GET
         operASInput.setJSON(jsonSearch);

         operASOutput = _AuthzServerDAO.execute(operASInput); // validate

         if (operASOutput.getState() == STATE.NOTEXIST) // 404 NOT FOUND
         {
            jsonOutput = new JSONObject();
            jsonOutput.put(ConstantsIF.RESULTS, new JSONArray());
         } else {
            jsonOutput = operASOutput.getJSON();
         }

         operOutput.setState(operASOutput.getState());
         operOutput.setStatus(operASOutput.getStatus());
         operOutput.setJSON(jsonOutput);
      } else {
         throw new Exception(METHOD + ": access_token is empty");
      }

      _logger.exiting(CLASS, METHOD);

      return operOutput;
   }

   /**
    * Implementation of the "read" operation. Get "register" data from the
    * Auhorization Server, using the uid
    *
    * <pre>
    * JSON input ...
    * {
    *   "uid" : "...", // Register Id
    *   "access_token": "..."
    * }
    * JSON output ... 
    * {
    *   "resource_scopes": ["scope1","scope2","scope3"],
    *   "name": "...",
    *   "_id": "...",
    *   "type": "...",
    *   "labels": [],
    *   "user_access_policy_uri": "https://.../openam/XUI/?realm=/#uma/share/..."
    * }
    * curl example:
    * curl -X GET\
    * -H "Authorization: Bearer <<access_token>>" \
    * https://.../openam/uma/realms/root/resource_set/<<uid>>
    * {
    *   "resource_scopes": ["read","view","print"],
    *   "name": "...",
    *   "_id": "<<uid>>",
    *   "type": "...",
    *   "icon_uri": "...",
    *   "labels": ["VIP","3D"],
    *   "user_access_policy_uri": ".../openam/XUI/?realm=/#uma/share/<<uid>>"
    * }
    * </pre>
    * 
    * @param jsonInput JSONObject input
    * @return JSONObject output
    * @throws Exception
    */
   private JSONObject readImpl(final JSONObject jsonInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String access_token = null;
      String registerId = null;
      OperationIF operInput = null;
      OperationIF operOutput = null;
      JSONObject jsonHeaders = null;
      JSONObject jsonData = null;
      JSONObject jsonOutput = null;

      _logger.entering(CLASS, METHOD);

      access_token = JSON.getString(jsonInput, ConstantsIF.ACCESS_TOKEN);

      if (!STR.isEmpty(access_token)) {
         registerId = JSON.getString(jsonInput, ConstantsIF.UID);

         if (!STR.isEmpty(registerId)) {
            jsonHeaders = new JSONObject();
            jsonHeaders.put(ConstantsIF.AUTHORIZATION, "Bearer " + access_token);

            jsonData = new JSONObject();
            jsonData.put(ConstantsIF.HEADERS, jsonHeaders);
            jsonData.put(ConstantsIF.UID, registerId);
            jsonData.put(ConstantsIF.PATH, _path);

            operInput = new Operation(OperationIF.TYPE.READ); // GET
            operInput.setJSON(jsonData);

            operOutput = _AuthzServerDAO.execute(operInput);

            if (operOutput.getState() == STATE.SUCCESS) {
               jsonOutput = operOutput.getJSON();

               if (jsonOutput == null || jsonOutput.isEmpty()) {
                  throw new Exception(METHOD + ": JSON output is empty: " + operOutput.getStatus());
               }
            } else if (operOutput.getState() == STATE.NOTEXIST) {
               jsonOutput = null;
            } else {
               throw new Exception(METHOD + ": Could not read registered resource: " + operOutput.getStatus());
            }
         } else {
            throw new Exception(METHOD + ": registered resource id is empty");
         }
      } else {
         throw new Exception(METHOD + ": access_token is empty");
      }

      _logger.exiting(CLASS, METHOD);

      return jsonOutput;
   }

   /**
    * Implementation of the "replace" operation. Read the existing registered
    * resource, using the "uid" Merge the input attributes into the retieved
    * registered resource
    *
    * <pre>
    * JSON input ...
    * {
    *   "data": {
    *     "name": "", 
    *     "type": "",
    *     "resource_scopes": [ "view", ...  ],
    *     "icon_uri": ...
    *   },
    *   "access_token": "...",
    *   "uid": "..."
    * }     
    *
    * curl example:
    * curl -X PUT \
    * -H "Authorization: Bearer <<access_token>>" \
    * --data \
    * '{
    *    "name" : "...",
    *    "icon_uri" : "...",
    *    "resource_scopes" : ["delete","edit","view","print"],
    *    "labels" : ["...","..."],
    *    "type" : "..."
    * }' \
    * https://.../openam/uma/realms/root/resource_set/<<uid>>
    * {
    *   "_id": "...",
    *   "user_access_policy_uri": ".../openam/XUI/?realm=/#uma/share/..."
    * }
    * </pre>
    * 
    * @param jsonInput JSONObject input
    * @throws Exception
    */
   private void replaceImpl(final JSONObject jsonInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String access_token = null;
      OperationIF operReplaceInput = null;
      OperationIF operReplaceOutput = null;
      JSONObject jsonData = null;
      JSONObject jsonReadData = null;
      JSONObject jsonReplaceData = null;
      JSONObject jsonReplaceInput = null;
      JSONObject jsonRegisterHeaders = null;

      _logger.entering(CLASS, METHOD);

      access_token = JSON.getString(jsonInput, ConstantsIF.ACCESS_TOKEN);

      if (STR.isEmpty(access_token)) {
         throw new Exception(METHOD + ": access_token is empty");
      }

      jsonReadData = this.readImpl(jsonInput);

      if (jsonReadData == null || jsonReadData.isEmpty()) {
         throw new Exception("Registered resource has no data (JSON is empty)");
      }

      jsonData = JSON.getObject(jsonInput, ConstantsIF.DATA);

      if (jsonData == null || jsonData.isEmpty()) {
         throw new Exception("Input JSON data is empty");
      }

      jsonReplaceData = jsonReadData;

      for (Object o : jsonData.keySet()) {
         jsonReplaceData.put(o.toString(), jsonData.get(o.toString()));
      }

      jsonRegisterHeaders = new JSONObject();
      jsonRegisterHeaders.put(ConstantsIF.CONTENT_TYPE, ConstantsIF.APPLICATION_JSON);
      jsonRegisterHeaders.put(ConstantsIF.AUTHORIZATION, "Bearer " + access_token);

      jsonReplaceInput = new JSONObject();
      jsonReplaceInput.put(ConstantsIF.HEADERS, jsonRegisterHeaders);
      jsonReplaceInput.put(ConstantsIF.DATA, jsonReplaceData);
      jsonReplaceInput.put(ConstantsIF.UID, jsonInput.get(ConstantsIF.UID));
      jsonReplaceInput.put(ConstantsIF.PATH, _path);

      operReplaceInput = new Operation(OperationIF.TYPE.REPLACE);
      operReplaceInput.setJSON(jsonReplaceInput);

      operReplaceOutput = _AuthzServerDAO.execute(operReplaceInput);

      if (operReplaceOutput.isError()) {
         throw new Exception(
               METHOD + ": " + operReplaceOutput.getState().toString() + ": " + operReplaceOutput.getStatus());
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }

   /**
    * Implementation of the "delete" operation. Read the existing entry using the
    * "uid" If the "content" attribute value exists, (uid for the Content Server) -
    * Delete the "data" on the Content Server
    * 
    * <pre>
    * curl example:
    * curl -X DELETE \
    * -H "Authorization: Bearer <<access_token>>" \
    * https://.../openam/uma/realms/root/resource_set/<<registerId>>
    * </pre>
    *
    * @param jsonInput JSONObject input
    * @throws Exception
    */
   private void deleteImpl(final JSONObject jsonInput) throws Exception {
      String METHOD = Thread.currentThread().getStackTrace()[1].getMethodName();
      String access_token = null;
      String registerId = null;
      OperationIF operDeleteInput = null;
      OperationIF operDeleteOutput = null;
      JSONObject jsonData = null;
      JSONObject jsonHeaders = null;

      _logger.entering(CLASS, METHOD);

      access_token = JSON.getString(jsonInput, ConstantsIF.ACCESS_TOKEN);

      if (STR.isEmpty(access_token)) {
         throw new Exception(METHOD + ": access_token is empty");
      } else {
         registerId = JSON.getString(jsonInput, ConstantsIF.UID);

         if (!STR.isEmpty(registerId)) {
            jsonHeaders = new JSONObject();
            jsonHeaders.put(ConstantsIF.AUTHORIZATION, "Bearer " + access_token);

            jsonData = new JSONObject();
            jsonData.put(ConstantsIF.HEADERS, jsonHeaders);
            jsonData.put(ConstantsIF.UID, registerId);
            jsonData.put(ConstantsIF.PATH, _path);

            operDeleteInput = new Operation(OperationIF.TYPE.DELETE); // DELETE
            operDeleteInput.setJSON(jsonData);

            operDeleteOutput = _AuthzServerDAO.execute(operDeleteInput);

            if (operDeleteOutput.getState() != STATE.SUCCESS && operDeleteOutput.getState() != STATE.NOTEXIST) {
               throw new Exception(
                     METHOD + ": Delete failed: STATE: " + operDeleteOutput.getState().toString() + ", STATUS: "
                           + operDeleteOutput.getStatus() + ", JSON: " + operDeleteOutput.getJSON().toString());
            }
         } else {
            throw new Exception(METHOD + ": registered resource id is empty");
         }
      }

      _logger.exiting(CLASS, METHOD);

      return;
   }
}
