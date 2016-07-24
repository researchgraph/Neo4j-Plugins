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
 */

package org.researchgraph.neo4j.plugings.auth.test;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;

import javax.ws.rs.core.HttpHeaders;

import org.codehaus.jackson.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.researchgraph.neo4j.plugings.auth.AuthService;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.harness.junit.Neo4jRule;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.test.server.HTTP;
import org.neo4j.test.server.HTTP.RawPayload;
import org.neo4j.test.server.HTTP.Response;

import com.sun.jersey.core.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

// make one version of neo4j

public class AuthServiceTest {
	private static final File AUTH = new File( "neo4j-home/data/dbms/authorization" );
		
	@Before
	@After
	public void deleteAuthorization() {
		// Delete authorization file before each test to make sure we are begin the test in the same environment
		if (AUTH.exists())
			AUTH.delete();
	}
	
	@Rule
	public Neo4jRule neo4j = new Neo4jRule()
		.withConfig( GraphDatabaseSettings.auth_enabled.name(), Boolean.toString( true ) )
		.withConfig( GraphDatabaseSettings.auth_store.name(), AUTH.toString() )
		.withExtension( "/unmanaged", AuthService.class );
		
	private String generateSecret( String username, String password )
	{
		return "Basic " + base64( username + ":" + password );
	}
	
	private String base64(String value)
	{
		return new String( Base64.encode( value ), Charset.forName( "UTF-8" ));
	}
	
	
	private Response getGraphDatabase() {
		URI serverURI = neo4j.httpURI().resolve( "db/graph/" );
		
	    return HTTP.GET( serverURI.toString() ); 
	}
	
	private Response getGraphDatabaseWithUser(String user, String password) {
		URI serverURI = neo4j.httpURI().resolve( "db/graph/" );
		
	    return HTTP
	    		.withHeaders( HttpHeaders.AUTHORIZATION, generateSecret( user, password ) )
	    		.GET( serverURI.toString() ); 
	}
	
	private Response getUser(String user, String password) {
		URI serverURI = neo4j.httpURI().resolve( String.format( "user/%s", user ) );
			
	    return HTTP
	    		.withHeaders( HttpHeaders.AUTHORIZATION, generateSecret( user, password ) )
		    	.GET( serverURI.toString() ); 
	}
	
	private Response changePassword(String user, String password, String admin, String adminPassword) {
		URI serverURI = neo4j.httpURI().resolve( String.format( "user/%s/password", user ) );
		RawPayload payload = RawPayload.quotedJson( String.format( "{'password':'%s'}", password ) );
			
		return HTTP
				.withHeaders(HttpHeaders.AUTHORIZATION, generateSecret( admin, adminPassword ))
		    	.POST( serverURI.toString(), payload ); 
	}
	
	private Response createNewUser(String user, String password, String admin, String adminPassword) {
		URI serverURI = neo4j.httpURI().resolve( String.format( "unmanaged/auth/add/%s", user ) );
		//RawPayload payload = RawPayload.rawPayload( String.format( "password=%s", password ) );
		RawPayload payload = RawPayload.quotedJson( String.format( "{'password':'%s'}", password ) );
		
	    return HTTP
	    		.withHeaders(HttpHeaders.AUTHORIZATION, generateSecret( admin, adminPassword ))
	    		.POST( serverURI.toString(), payload );
	}
	
	private Response deleteUser(String user, String admin, String adminPassword) {
		URI serverURI = neo4j.httpURI().resolve( String.format( "unmanaged/auth/delete/%s", user ) );
		
	    return HTTP
	    		.withHeaders(HttpHeaders.AUTHORIZATION, generateSecret( admin, adminPassword ))
	    		.GET( serverURI.toString() );
	}
				
	
	@Test
	public void shouldDenyAccessWithoutPassword() throws Exception
	{
		// If we connect to the graph database without a password
		Response response = getGraphDatabase();
		
	    // Then it should reply
		assertThat(response.status(), equalTo(401));
	    
	    JsonNode data = JsonHelper.jsonNode( response.rawContent() );
	    JsonNode firstError = data.get( "errors" ).get( 0 );
	    // And code type should be correct
	    assertThat( firstError.get( "code" ).asText(), equalTo( "Neo.ClientError.Security.Unauthorized" ) );
	    // And code message should indicate that there is no header
	    assertThat( firstError.get( "message" ).asText(), equalTo( "No authentication header supplied." ) );
	}
	
	@Test
	public void shouldAccessNeo4jWithDefaultPassword() throws Exception
	{
		// If we connect to the user record with default password
	    Response response = getUser("neo4j", "neo4j");
	    
	    // Then it should reply
	    assertThat(response.status(), equalTo(200));	    

	    JsonNode data = JsonHelper.jsonNode( response.rawContent() );
	    // And username should be neo4j
	    assertThat( data.get( "username" ).asText(), equalTo( "neo4j" ) );
	    // And it should require password change
	    assertThat( data.get( "password_change_required" ).asText(), equalTo( Boolean.toString(true) ) );
	}
	
	@Test
	public void shouldRequirePasswordChange() throws Exception
	{
		// If we access database with default user and password
	    Response response = getGraphDatabaseWithUser("neo4j", "neo4j"); 
	    
	    // Then it should reply
	    assertThat(response.status(), equalTo(403));	    

	    JsonNode data = JsonHelper.jsonNode( response.rawContent() );
	    JsonNode firstError = data.get( "errors" ).get( 0 );
	    // And code type should be correct
	    assertThat( firstError.get( "code" ).asText(), equalTo( "Neo.ClientError.Security.Forbidden" ) );
	    // And code message should indicate that there is no header
	    assertThat( firstError.get( "message" ).asText(), equalTo( "User is required to change their password." ) );
	}
		
	@Test
	public void shouldAccessWithNewPassword() throws Exception
	{
		// If we access database with default user and password
		Response response = getUser("neo4j", "neo4j");
		
		// Then it should reply
		assertThat(response.status(), equalTo(200));
		
		// If we attempt to change password for the default user to enable it ussage
		response = changePassword("neo4j", "secret", "neo4j", "neo4j");
		
		// Then it should reply
		assertThat(response.status(), equalTo(200));
		
		// If we access database with using new user passsword
		response = getUser("neo4j", "secret");
	    
	    // Then it should reply
		assertThat(response.status(), equalTo(200));	
	    
	    JsonNode data = JsonHelper.jsonNode( response.rawContent() );
	    assertThat( data.get( "username" ).asText(), equalTo( "neo4j" ) );
	    assertThat( data.get( "password_change_required" ).asText(), equalTo( Boolean.toString(false) ) );	    
	}
	
		
	@Test
	public void shouldCreateAndDeleteUser() throws Exception
	{
		// If we access database with default user and password
		Response response = getUser("neo4j", "neo4j");
		
		// Then it should reply 200
		assertThat(response.status(), equalTo(200));
		
		// If we attempt to change password for the default user to enable it ussage
		response = changePassword("neo4j", "secret", "neo4j", "neo4j");
		
		// Then it should reply 200
		assertThat(response.status(), equalTo(200));
		
		// If we access database with using new user password
		response = getUser("neo4j", "secret");
	    
	    // Then it should reply 200
		assertThat(response.status(), equalTo(200));	
		
		// If attempt to create new user, using default user as admin
		response = createNewUser("test", "test", "neo4j", "secret");

	    // Then it should reply 200
		assertThat(response.status(), equalTo(200));
	    
	    // If we access database with new user and passsword
	    response = getUser("test", "test");
	    
	    // Then it should reply 200
		assertThat(response.status(), equalTo(200));	
		
		JsonNode data = JsonHelper.jsonNode( response.rawContent() );
		// And username should be test
		assertThat( data.get( "username" ).asText(), equalTo( "test" ) );
		// And it should require password change
		assertThat( data.get( "password_change_required" ).asText(), equalTo( Boolean.toString(true) ) );
		
	    // If we attempt to change password for the new user 
	 	response = changePassword("test", "test123", "test", "test");
		
		// Then it should reply 200
		assertThat(response.status(), equalTo(200));
		
		// If we access database with new user and passsword
	    response = getUser("test", "test123");
	    
	    // Then it should reply 200
		assertThat(response.status(), equalTo(200));	
		
		// If we attempt to delete new user 
		response = deleteUser("test", "neo4j", "secret");
		
		// Then it should reply 200
		assertThat(response.status(), equalTo(200));
		
		// If we access database with deleted user
		response = getUser("test", "test");
		    
		// Then it should reply 401
		assertThat(response.status(), equalTo(401));
	}
}
