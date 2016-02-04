package web;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
 
public class TestingHandler extends AbstractHandler
{
    final String _greeting;
    final String _body;
 
    public TestingHandler()
    {
        _greeting="Hello World";
        _body=null;
    }
 
    public TestingHandler(String greeting)
    {
        _greeting=greeting;
        _body=null;
    }
 
    public TestingHandler(String greeting,String body)
    {
        _greeting=greeting;
        _body=body;
    }
 
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        System.out.println("HelloHandler "+target);
    	response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
 
        response.getWriter().println("<h1>"+_greeting+"</h1>");
        if (_body!=null)
            response.getWriter().println(_body);
    }
}