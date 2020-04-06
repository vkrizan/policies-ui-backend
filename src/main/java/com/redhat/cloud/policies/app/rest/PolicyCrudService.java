/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.cloud.policies.app.rest;

import com.redhat.cloud.policies.app.PolicyEngine;
import com.redhat.cloud.policies.app.auth.RhIdPrincipal;
import com.redhat.cloud.policies.app.model.engine.FullTrigger;
import com.redhat.cloud.policies.app.model.Msg;
import com.redhat.cloud.policies.app.model.Policy;
import java.net.ConnectException;
import java.net.URI;
import java.util.UUID;
import java.util.logging.Logger;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.redhat.cloud.policies.app.model.pager.Page;
import com.redhat.cloud.policies.app.model.pager.Pager;
import com.redhat.cloud.policies.app.rest.utils.PagingUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.metrics.annotation.SimplyTimed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameters;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.exception.ConstraintViolationException;

/**
 * @author hrupp
 */
@Path("/policies")
@Produces("application/json")
@Consumes("application/json")
@SimplyTimed(absolute = true, name="PolicySvc")
@RequestScoped
public class PolicyCrudService {

  Logger log = Logger.getLogger(this.getClass().getSimpleName());

  @ConfigProperty(name = "engine.skip", defaultValue = "false")
  boolean skipEngineCall;

  @Inject
  @RestClient
  PolicyEngine engine;

  @Context
  UriInfo uriInfo;

  @SuppressWarnings("CdiInjectionPointsInspection")
  @Inject
  RhIdPrincipal user;

  @Inject
  EntityManager entityManager;

  @Operation(summary = "Return all policies for a given account")
  @GET
  @Path("/")
  @Parameters({
          @Parameter(
                  name = "offset",
                  in = ParameterIn.QUERY,
                  description = "Page number, starts 0, if not specified uses 0.",
                  schema = @Schema(type = SchemaType.INTEGER)
          ),
          @Parameter(
                  name = "limit",
                  in = ParameterIn.QUERY,
                  description = "Number of items per page, if not specified uses 10.",
                  schema = @Schema(type = SchemaType.INTEGER)
          ),
          @Parameter(
                  name = "sortColumn",
                  in = ParameterIn.QUERY,
                  description = "Column to sort the results by",
                  schema = @Schema(
                          type = SchemaType.STRING,
                          enumeration = {
                                  "name",
                                  "description",
                                  "is_enabled",
                                  "mtime"
                          }
                  )
          ),
          @Parameter(
                  name = "sortDirection",
                  in = ParameterIn.QUERY,
                  description = "Sort direction used",
                  schema = @Schema(
                          type = SchemaType.STRING,
                          enumeration = {
                                  "asc",
                                  "desc"
                          }
                  )
          ),
          @Parameter(
                  name = "filter[name]",
                  in = ParameterIn.QUERY,
                  description = "Filtering policies by the name depending on the Filter operator used.",
                  schema = @Schema(type = SchemaType.STRING)
          ),
          @Parameter(
                  name="filter:op[name]",
                  in = ParameterIn.QUERY,
                  description = "Operations used with the filter",
                  schema = @Schema(
                          type = SchemaType.STRING,
                          enumeration = {
                                  "equal",
                                  "like",
                                  "ilike",
                                  "not_equal",
                                  "boolean_is"
                          },
                          defaultValue = "equal"
                  )
          ),
          @Parameter(
                  name = "filter[description]",
                  in = ParameterIn.QUERY,
                  description = "Filtering policies by the description depending on the Filter operator used.",
                  schema = @Schema(type = SchemaType.STRING)
          ),
          @Parameter(
                  name="filter:op[description]",
                  in = ParameterIn.QUERY,
                  description = "Operations used with the filter",
                  schema = @Schema(
                          type = SchemaType.STRING,
                          enumeration = {
                                  "equal",
                                  "like",
                                  "ilike",
                                  "not_equal",
                                  "boolean_is"
                          },
                          defaultValue = "equal"
                  )
          ),
          @Parameter(
                  name = "filter[is_enabled]",
                  in = ParameterIn.QUERY,
                  description = "Filtering policies by the is_enabled field, depending on the Filter operator used.",
                  schema = @Schema(type = SchemaType.STRING)
          ),
          @Parameter(
                  name="filter:op[is_enabled]",
                  in = ParameterIn.QUERY,
                  description = "Operations used with the filter",
                  schema = @Schema(
                          type = SchemaType.STRING,
                          enumeration = {
                                  "equal",
                                  "like",
                                  "ilike",
                                  "not_equal",
                                  "boolean_is"
                          },
                          defaultValue = "equal"
                  )
          ),
  })
  @APIResponse(responseCode = "400", description = "Bad parameter for sorting was passed")
  @APIResponse(responseCode = "404", description = "No policies found for customer")
  @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action")
  @APIResponse(responseCode = "200", description = "Policies found", content =
                 @Content(schema = @Schema(implementation = PagingUtils.PagedResponse.class)),
                 headers = @Header(name = "TotalCount", description = "Total number of items found",
                                   schema = @Schema(type = SchemaType.INTEGER)))
  public Response getPoliciesForCustomer() {

    if (!user.canReadAll()) {
      return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to retrieve policies")).build();
    }

    Pager pager = PagingUtils.extractPager(uriInfo);
    Page<Policy> page;
    try {
      page = Policy.pagePoliciesForCustomer(entityManager, user.getAccount(), pager);
      // TODO once the engine supports batching, rewrite this.
      page.stream().forEach(p -> {
        try {
          FullTrigger ft = engine.fetchTrigger(p.id, user.getAccount());
          if (ft.conditions != null && !ft.conditions.isEmpty()) {
            p.setLastEvaluation(ft.conditions.get(0).lastEvaluation);
          }
        } catch (Exception e) {
          p.setLastEvaluation(0);
        }
      });

    } catch (IllegalArgumentException iae) {
      return Response.status(400,iae.getLocalizedMessage()).build();
    }

    return PagingUtils.responseBuilder(page).build();
  }

  @Operation(summary = "Validate (and possibly persist) a passed policy for the given account")
  @Parameter(name = "alsoStore",
             description = "If passed and set to true, the passed policy is also persisted (if it is valid)")
  @APIResponses({
      @APIResponse(responseCode = "500", description = "No policy provided or internal error"),
      @APIResponse(responseCode = "400", description = "Policy validation failed",
                   content = @Content(schema =@Schema(implementation = Msg.class,
                                                                         description = "Reason for failure"))),
      @APIResponse(responseCode = "409", description = "Persisting failed",
                   content = @Content(schema =@Schema(implementation = Msg.class,
                                                      description = "Reason for failure"))),
      @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action"),
      @APIResponse(responseCode = "201", description = "Policy persisted",
                   content = @Content(schema = @Schema(implementation = Policy.class))),
      @APIResponse(responseCode = "200", description = "Policy validated")
                })
  @POST
  @Path("/")
  @Transactional
  public Response storePolicy(@QueryParam ("alsoStore") boolean alsoStore, @Valid Policy policy) {

    if (!user.canReadAll()) {
      return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to verify policy")).build();
    }

    if (policy==null) {
      return Response.status(500, "No policy passed").build();
    }

    policy.id = null;
    policy.customerid = user.getAccount();

    Policy tmp = Policy.findByName(user.getAccount(), policy.name);
    if (tmp != null) {
      return Response.status(409).entity(new Msg("Policy name is not unique")).build();
    }

    if (!skipEngineCall) {
      try {
        FullTrigger trigger = new FullTrigger(policy,true);
        engine.storeTrigger(trigger, true, user.getAccount());
      } catch (Exception e) {
        return Response.status(400,e.getMessage()).entity(getEngineExceptionMsg(e)).build();
      }
    }

    if (!alsoStore) {
      return Response.status(200).entity(new Msg("Policy validated")).build();
    }

    if (!user.canWriteAll()) {
      return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to store policy")).build();
    }

    // Basic validation was successful, so try to persist.
    // This may still fail du to unique name violation, so
    // we need to check for that.
    UUID id;
    try {
      id = policy.store(user.getAccount(), policy);
      // We persisted locally, now tell the engine
      if (!skipEngineCall) {
        FullTrigger trigger = new FullTrigger(policy);
        try {
          engine.storeTrigger(trigger, false, user.getAccount());
        } catch (Exception e) {
          return Response.status(400,e.getMessage()).entity(getEngineExceptionMsg(e)).build();
        }
      }
    } catch (Throwable t) {
      return getResponseSavingPolicyThrowable(t);
    }

    // Policy is persisted. Return its location.
    URI location =
        UriBuilder.fromResource(PolicyCrudService.class).path(PolicyCrudService.class, "getPolicy").build(id);
    ResponseBuilder builder = Response.created(location).entity(policy);
    return builder.build();

  }

  private Response getResponseSavingPolicyThrowable(Throwable t) {
    if (t instanceof PersistenceException && t.getCause() instanceof ConstraintViolationException) {
      return Response.status(409, t.getMessage()).entity(new Msg("Constraint violation")).build();
    } else {
      t.printStackTrace();
      return Response.status(500, t.getMessage()).build();
    }
  }

  private Msg getEngineExceptionMsg(Exception e) {
    Msg msg;
    if (e instanceof RuntimeException && e.getCause() instanceof ConnectException) {
      msg = new Msg("Connection to backend-engine failed. Please retry later");
    } else {
      msg = new Msg(e.getMessage());
    }
    return msg;
  }

  @Operation(summary = "Delete a single policy for a customer by its id")
  @DELETE
  @Path("/{id}")
  @APIResponse(responseCode = "200", description = "Policy deleted")
  @APIResponse(responseCode = "404", description = "Policy not found")
  @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action")
  @Transactional
  public Response deletePolicy(@PathParam("id") UUID policyId) {

    if (!user.canWriteAll()) {
       return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to delete policy")).build();
    }
    Policy policy = Policy.findById(user.getAccount(), policyId);

    ResponseBuilder builder ;
    if (policy==null) {
      builder = Response.status(Response.Status.NOT_FOUND);
    } else {
      policy.delete(policy);
      try {
        engine.deleteTrigger(policy.id, user.getAccount());
        builder = Response.ok(policy);
      } catch (NotFoundException nfe) {
        // Engine does not have it - we can delete anyway
        builder = Response.ok(policy);
      } catch (Exception e) {
        e.printStackTrace();  // TODO: Customise this generated block
        builder = Response.serverError().entity(new Msg(e.getMessage()));
      }
    }

    return builder.build();

  }

  @Operation(summary = "Enable/disable a policy")
  @Parameter(name = "id", description = "ID of the Policy")
  @Parameter(name = "enabled",
      schema =  @Schema(type = SchemaType.BOOLEAN, defaultValue = "false"),
      description = "Should the policy be enabled (true) or disabled (false, default)")
  @APIResponse(responseCode = "200", description = "Policy updated")
  @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action")
  @APIResponse(responseCode = "404", description = "Policy not found")
  @APIResponse(responseCode = "500", description = "Updating failed")
  @POST
  @Path("/{id}/enabled")
  @Transactional
  public Response setEnabledStateForPolicy(@PathParam("id") UUID policyId, @QueryParam("enabled") boolean shouldBeEnabled) {
    if (!user.canWriteAll()) {
       return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to update policy")).build();
     }

    Policy storedPolicy = Policy.findById(user.getAccount(), policyId);

    ResponseBuilder builder ;
    if (storedPolicy==null) {
      builder = Response.status(404, "Original policy not found");}
    else {
      try {
        if (shouldBeEnabled) {
          engine.enableTrigger(storedPolicy.id, user.getAccount());
        } else {
          engine.disableTrigger(storedPolicy.id, user.getAccount());
        }
        storedPolicy.isEnabled = shouldBeEnabled;
        storedPolicy.persist();
        builder = Response.ok();
      } catch (NotFoundException nfe) {
        builder = Response.status(404, "Policy not found in engine");
        log.warning("Enable/Disable failed, policy [" + storedPolicy.id + "] not found in engine");
      }
      catch (Exception e ) {
        builder = Response.status(500, "Update failed: " + e.getMessage());
      }
    }
    return builder.build();
  }

  @Operation(summary = "Update a single policy for a customer by its id")
  @PUT
  @Path("/{policyId}")
  @APIResponse(responseCode = "200", description = "Policy updated or policy validated")
  @APIResponse(responseCode = "400", description = "Invalid policy provided")
  @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action")
  @APIResponse(responseCode = "404", description = "Policy did not exist - did you store it?")
  @Transactional
  public Response updatePolicy(@QueryParam ("dry") boolean dryRun, @PathParam("policyId") UUID policyId,
                               @Valid Policy policy) {

    if (!user.canWriteAll()) {
       return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to update policy")).build();
     }

    Policy storedPolicy = Policy.findById(user.getAccount(), policyId);

    ResponseBuilder builder ;
    if (storedPolicy==null) {
      builder = Response.status(404, "Original policy not found");
    } else {
      if (!policy.id.equals(policyId)) {
        builder = Response.status(400, "Invalid policy");
      } else {

        Policy tmp = Policy.findByName(user.getAccount(), policy.name);
        if (tmp != null && !tmp.id.equals(policy.id)) {
          return Response.status(409).entity(new Msg("Policy name is not unique")).build();
        }

        if (!skipEngineCall) {
          try {
            FullTrigger trigger = new FullTrigger(policy);
            engine.updateTrigger(policy.id, trigger, true, user.getAccount());
          } catch (Exception e) {
            return Response.status(400,e.getMessage()).entity(getEngineExceptionMsg(e)).build();
          }
        }

        if (dryRun) {
          return Response.status(200).entity(new Msg("Policy validated")).build();
        }

        // All is good, we can now do the real work
        // The engine requires that we update existing structures,
        // so we need to first poll from it.
        try {
          FullTrigger existingTrigger;
          if (!skipEngineCall) {
            try {
              existingTrigger = engine.fetchTrigger(storedPolicy.id, user.getAccount());
            } catch (Exception e) {
              return Response.status(400, e.getMessage()).entity(getEngineExceptionMsg(e)).build();
            }
          }
          else {
            // To make the compiler happy
            existingTrigger = new FullTrigger(storedPolicy);
          }

          storedPolicy.populateFrom(policy);
          storedPolicy.customerid = user.getAccount();
          storedPolicy.setMtimeToNow();

          existingTrigger.updateFromPolicy(storedPolicy);
          if (!skipEngineCall) {
            try {
              engine.updateTrigger(storedPolicy.id, existingTrigger, false, user.getAccount());
              Policy.persist(storedPolicy);
            } catch (Exception e) {
              return Response.status(400, e.getMessage()).entity(getEngineExceptionMsg(e)).build();
            }
          }
          Policy.persist(storedPolicy);
        } catch (Throwable t) {
          return getResponseSavingPolicyThrowable(t);
        }

        builder = Response.ok(storedPolicy);
      }
    }

    return builder.build();
  }

  @Operation(summary = "Validates a Policy condition")
  @POST
  @Path("/validate")
  @APIResponses({
          @APIResponse(responseCode = "200", description = "Condition validated"),
          @APIResponse(responseCode = "400", description = "Condition not valid"),
          @APIResponse(responseCode = "500", description = "No policy provided or internal error")
  })
  public Response validateCondition(Policy policy) {

    if (!user.canReadAll()) {
      return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to verify policy")).build();
    }

    if (policy == null) {
      return Response.status(500, "No policy passed").build();
    }

    policy.customerid = user.getAccount();

    if (!skipEngineCall) {
      try {
        FullTrigger trigger = new FullTrigger(policy, policy.id == null);
        if (policy.id == null) {
          engine.storeTrigger(trigger, true, user.getAccount());
        } else {
          engine.updateTrigger(policy.id, trigger, true, user.getAccount());
        }
      } catch (Exception e) {
        return Response.status(400,e.getMessage()).entity(getEngineExceptionMsg(e)).build();
      }
    }

    return Response.status(200).entity(new Msg("Policy.condition validated")).build();

  }


  @Operation(summary = "Retrieve a single policy for a customer by its id")
  @GET
  @Path("/{id}")
  @APIResponse(responseCode = "200", description = "Policy found", content =
                 @Content(schema = @Schema(implementation = Policy.class)))
  @APIResponse(responseCode = "404", description = "Policy not found")
  @APIResponse(responseCode = "403", description = "Individual permissions missing to complete action")
  public Response getPolicy(@PathParam("id") UUID policyId) {

    if (!user.canReadAll()) {
      return Response.status(Response.Status.FORBIDDEN).entity(new Msg("Missing permissions to retrieve policies")).build();
    }

    Policy policy = Policy.findById(user.getAccount(), policyId);

    ResponseBuilder builder ;
    if (policy==null) {
      builder = Response.status(Response.Status.NOT_FOUND);
    } else {
      if (!skipEngineCall) {
        try {
          FullTrigger ft = engine.fetchTrigger(policyId, user.getAccount());
          if (ft.conditions != null && !ft.conditions.isEmpty()) {
            policy.setLastEvaluation(ft.conditions.get(0).lastEvaluation);
          }
        } catch (Exception e) {
          policy.setLastEvaluation(0);
        }
      }
      builder = Response.ok(policy);
      EntityTag etag = new EntityTag(String.valueOf(policy.hashCode()));
      builder.header("ETag",etag);
    }

    return builder.build();
  }

}