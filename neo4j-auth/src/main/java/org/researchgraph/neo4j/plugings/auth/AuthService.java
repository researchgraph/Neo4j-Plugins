/**
 * The MIT License (MIT)
 * 
 * Copyright (c) 2015 rd-switchboard
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * 
 * The original user creation code has been taken from: https://github.com/rbramley/neo4j-useradd
 * This plugin is compatible with Neo4j 2.3.1 and should be compatible with future versions as well.   
 */

package org.researchgraph.neo4j.plugings.auth;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.server.rest.repr.BadInputException;
import org.neo4j.server.rest.repr.ExceptionRepresentation;
import org.neo4j.server.rest.repr.InputFormat;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.transactional.error.Neo4jError;
import org.neo4j.server.security.auth.AuthManager;
import org.neo4j.server.security.auth.User;
import org.neo4j.server.security.auth.exception.IllegalCredentialsException;


import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.neo4j.server.rest.web.CustomStatusType.UNPROCESSABLE;

@Path( "/auth" )
public class AuthService {

	private final AuthManager authManager;
    private final InputFormat input;
    private final OutputFormat output;
    public static final String NEO4J_USER = "neo4j";
    public static final String PASSWORD = "password";

    public AuthService( @Context AuthManager authManager,
        @Context InputFormat input, @Context OutputFormat output )
    {
        this.authManager = authManager;
        this.input = input;
        this.output = output;
    }

    @POST
    @Path("/add/{username}")
    public Response addUser( @PathParam("username") String username, 
    		@Context HttpServletRequest req, String payload )
    {
        Principal principal = req.getUserPrincipal();
        if ( principal == null || !principal.getName().equals( NEO4J_USER ) )
        {
            return output.notFound();
        }

        final Map<String, Object> deserialized;
        try
        {
            deserialized = input.readMap( payload );
        } catch ( BadInputException e )
        {
            return output.response( BAD_REQUEST, new ExceptionRepresentation(
            		new Neo4jError( Status.Request.InvalidFormat, e.getMessage() ) ) );
        }

        Object o = deserialized.get( PASSWORD );
        if ( o == null )
        {
            return output.response( UNPROCESSABLE, new ExceptionRepresentation(
            new Neo4jError( Status.Request.InvalidFormat, String.format( "Required parameter '%s' is missing.", PASSWORD ) ) ) );
        }
        if ( !( o instanceof String ) )
        {
            return output.response( UNPROCESSABLE, new ExceptionRepresentation(
            new Neo4jError( Status.Request.InvalidFormat, String.format( "Expected '%s' to be a string.", PASSWORD ) ) ) );
        }
        String newPassword = (String) o;
        if ( newPassword.length() == 0 )
        {
            return output.response( UNPROCESSABLE, new ExceptionRepresentation(
            new Neo4jError( Status.Request.Invalid, "Password cannot be empty." ) ) );
        }

        final User newUser;
        try
        {
            newUser = authManager.newUser( username, newPassword, true );
        } 
        catch ( IOException | IllegalCredentialsException e )
        {
            return output.serverErrorWithoutLegacyStacktrace( e );
        }

        if (newUser == null)
        {
            return output.notFound();
        }

        return output.ok();
    }
	
    @GET
    @Path("/delete/{username}")
    public Response deleteUser( @PathParam("username") String username, 
    		@Context HttpServletRequest req )
    {
        Principal principal = req.getUserPrincipal();
        if ( principal == null || !principal.getName().equals( NEO4J_USER ) )
        {
            return output.notFound();
        }

        try
        {
            if (!authManager.deleteUser( username )) {
            	return output.response( UNPROCESSABLE, new ExceptionRepresentation(
                        new Neo4jError( Status.Request.InvalidFormat, String.format( "Unable to delete user '%s'.", username ) ) ) );
            }
        } catch ( IOException e )
        {
            return output.serverErrorWithoutLegacyStacktrace( e );
        }

        return output.ok();
    }
	
}
