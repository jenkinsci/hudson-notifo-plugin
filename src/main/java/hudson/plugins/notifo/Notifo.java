package hudson.plugins.notifo;

import hudson.model.BuildListener;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;

public class Notifo
{
    private final String NOTIFO_URI = "https://api.notifo.com/v1/send_notification";
    private HttpClient client;
    private String serviceUser;
    private Iterable<String> userList;
    private String token;

    public Notifo( String serviceUser, String token, Iterable<String> userList )
    {
        this.serviceUser = serviceUser;
        this.userList = userList;
        this.token = token;
    }

    public void post( String body, BuildListener listener )
              throws IOException
    {
        createClient(  );

        for ( String username : this.userList )
        {
            PostMethod post = new PostMethod( NOTIFO_URI );
            NameValuePair[] data =
                { new NameValuePair( "to", username ), new NameValuePair( "msg", body ), new NameValuePair( "title",
                                                                                                            "Build Status" ) };
            post.setRequestBody( data );

            try
            {
                int rc;
                
                if ((rc=client.executeMethod( post )) != HttpStatus.SC_OK) {
                  listener.error("Bad status code %d received from Notifo for user %s", rc, username);
                }
            } catch ( Exception e )
            {
                e.printStackTrace( listener.error( "Unable to send message to Notifo API for username: %s", username ) );
            } finally
            {
                post.releaseConnection(  );
            }
        }
    }

    private void createClient(  )
    {
        client = new HttpClient(  );

        Credentials defaultcreds = new UsernamePasswordCredentials( serviceUser, token );
        client.getState(  ).setCredentials( AuthScope.ANY, defaultcreds );
        client.getParams(  ).setAuthenticationPreemptive( true );
    }
}
