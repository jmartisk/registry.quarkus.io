package io.quarkus.registry.app.resteasy;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .header("X-Reason", exception.getMessage())
                .build();
    }

}
