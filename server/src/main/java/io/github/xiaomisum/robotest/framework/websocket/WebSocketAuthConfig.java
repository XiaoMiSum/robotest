package io.github.xiaomisum.robotest.framework.websocket;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.migoo.framework.security.core.authentication.AuthUserDetailsFetcher;

import java.util.List;
import java.util.Map;

@Configuration
public class WebSocketAuthConfig {

    @Bean
    public ServerEndpointConfig.Configurator configurator(
            AuthUserDetailsFetcher<LoginUser> authUserDetailsFetcher) {
        return new ServerEndpointConfig.Configurator() {
            @Override
            public void modifyHandshake(ServerEndpointConfig sec,
                                         HandshakeRequest request,
                                         HandshakeResponse response) {
                Map<String, List<String>> params = request.getParameterMap();
                List<String> tokens = params.get(Constants.WebSocket.PARAM_TOKEN);
                if (tokens != null && !tokens.isEmpty()) {
                    String token = tokens.getFirst();
                    try {
                        LoginUser loginUser = authUserDetailsFetcher.verifyToken(token);
                        if (loginUser != null) {
                            sec.getUserProperties().put(Constants.WebSocket.PROP_USER_ID, loginUser.getId());
                            sec.getUserProperties().put(Constants.WebSocket.PROP_LOGIN_USER, loginUser);
                        }
                    } catch (Exception e) {
                        // Token invalid, userId stays null
                    }
                }
            }
        };
    }
}
