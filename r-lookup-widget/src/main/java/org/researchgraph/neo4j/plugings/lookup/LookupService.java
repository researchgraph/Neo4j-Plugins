package org.researchgraph.neo4j.plugings.lookup;

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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.codehaus.jackson.JsonEncoding;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.CypherException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.server.rest.repr.CypherResultRepresentation;
import org.neo4j.server.rest.repr.ExceptionRepresentation;
import org.neo4j.server.rest.repr.InputFormat;
import org.neo4j.server.rest.repr.OutputFormat;
import org.neo4j.server.rest.transactional.error.Neo4jError;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path("/lookup")
public class LookupService {
	private static final String PUBLICATIONS = "MATCH (n:publication) WHERE n.doi=~{doi} RETURN n";
	
	private final ObjectMapper objectMapper;
	
	private GraphDatabaseService graphDb;
	private final InputFormat input;
    private final OutputFormat output;
    
	public LookupService( @Context GraphDatabaseService graphDb,
			@Context InputFormat input, @Context OutputFormat output )
	{
		this.graphDb = graphDb;
		this.input = input;
		this.output = output;
		
		this.objectMapper = new ObjectMapper();
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
		
		StreamingOutput stream = new StreamingOutput()
        {
            @Override
            public void write( OutputStream os ) throws IOException, WebApplicationException
            {
                JsonGenerator jg = objectMapper.getJsonFactory().createJsonGenerator( os, JsonEncoding.UTF8 );
                jg.writeStartObject();
                jg.writeFieldName( "publications" );
                jg.writeStartArray();

                try ( Transaction tx = graphDb.beginTx();
                      Result result = graphDb.execute( PUBLICATIONS, params ) )
                {
                    while ( result.hasNext() )
                    {
                        Map<String,Object> row = result.next();
                        Node publication = (Node) row.get( "n" );
                        
                        jg.writeStartObject();
                        jg.writeStringField( "key", publication.getProperty( "key" ).toString() );
                        jg.writeStringField( "title", publication.getProperty( "title" ).toString() );
                        jg.writeStringField( "doi", publication.getProperty( "doi" ).toString() );
                        jg.writeEndObject();
                    }
                }

                jg.writeEndArray();
                jg.writeEndObject();
                jg.flush();
                jg.close();
            }
        };

        return Response.ok().entity( stream ).type( MediaType.APPLICATION_JSON ).build();
		
     /*  final Map<String, Object> params = MapUtil.map( "doi", decodedDoi );
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
		}*/
	}
}
