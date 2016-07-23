package org.researchgraph.neo4j.plugings.lookup;

import static org.neo4j.server.rest.web.CustomStatusType.UNPROCESSABLE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.CypherException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.server.rest.repr.CypherResultRepresentation;
import org.neo4j.server.rest.repr.ExceptionRepresentation;
import org.neo4j.server.rest.repr.InputFormat;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.repr.Representation;
import org.neo4j.server.rest.transactional.error.Neo4jError;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/lookup")
public class LookupService {
	private GraphDatabaseService graphDb;
	private final ObjectMapper objectMapper;    
	private final InputFormat input;
    private final OutputFormat output;
    
    private static final String PUBLICATIONS = "MATCH (n:publication) WHERE n.doi=~{doi} RETURN n.key AS key, n.title AS title";
	
	public LookupService( @Context GraphDatabaseService graphDb,
			@Context InputFormat input, @Context OutputFormat output )
	{
		this.graphDb = graphDb;
		this.objectMapper = new ObjectMapper();
		this.input = input;
		this.output = output;
	}

	@GET
  	@Path("/publication/doi/{doi}")
	public Response findColleagues( final @PathParam("doi") String doi ) throws UnsupportedEncodingException
	{
		final String decodedDoi = URLDecoder.decode(doi, StandardCharsets.UTF_8.name());
		if (decodedDoi.contains("'")) {
			return output.response( BAD_REQUEST, new ExceptionRepresentation(
					new Neo4jError( Status.Request.InvalidFormat, "DOI contains invalid symbols" ) ) );
		}
		
        final Map<String, Object> params = MapUtil.map( "doi", decodedDoi );
		try ( Transaction tx = graphDb.beginTx();
			  Result result =  graphDb.execute(PUBLICATIONS, params ) )
        {
			return output.ok(new CypherResultRepresentation( result, false, false) );
        }
		catch(Throwable e) {
			if (e.getCause() instanceof CypherException)
            {
                return output.badRequest( e.getCause() );
            } else {
                return output.badRequest( e );
            }
		}
	}
}
