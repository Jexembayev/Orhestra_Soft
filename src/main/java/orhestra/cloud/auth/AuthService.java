package orhestra.cloud.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import yandex.cloud.api.compute.v1.ImageServiceGrpc;
import yandex.cloud.api.compute.v1.InstanceServiceGrpc;
import yandex.cloud.api.operation.OperationServiceGrpc;
import yandex.cloud.sdk.ServiceFactory;
import yandex.cloud.sdk.auth.Auth;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class AuthService {
        private final ServiceFactory factory;
        private final OperationServiceGrpc.OperationServiceBlockingStub operationService;
        private final InstanceServiceGrpc.InstanceServiceBlockingStub instanceService;
        private final ImageServiceGrpc.ImageServiceBlockingStub imageService;

        /** Create using OAUTH_TOKEN from environment variable. Default endpoint. */
        public AuthService() {
                this(null, null);
        }

        /**
         * Create using explicit OAuth token (falls back to ENV if null). Default
         * endpoint.
         */
        public AuthService(String oauthToken) {
                this(oauthToken, null);
        }

        /**
         * Create using explicit OAuth token and a custom API endpoint.
         * endpoint example: "api.yandexcloud.kz" for Kazakhstan, null = default RU
         * endpoint.
         *
         * For KZ endpoint, OAuth is pre-exchanged to IAM via iam.api.yandexcloud.kz
         * because the SDK's built-in OAuth exchange uses the Russian IAM server.
         */
        public AuthService(String oauthToken, String endpoint) {
                this.factory = buildFactory(oauthToken, endpoint);

                this.operationService = factory.create(
                                OperationServiceGrpc.OperationServiceBlockingStub.class,
                                OperationServiceGrpc::newBlockingStub);
                this.instanceService = factory.create(
                                InstanceServiceGrpc.InstanceServiceBlockingStub.class,
                                InstanceServiceGrpc::newBlockingStub);
                this.imageService = factory.create(
                                ImageServiceGrpc.ImageServiceBlockingStub.class,
                                ImageServiceGrpc::newBlockingStub);
        }

        private static ServiceFactory buildFactory(String oauthToken, String endpoint) {
                var builder = ServiceFactory.builder()
                                .requestTimeout(Duration.ofMinutes(1));

                if (endpoint != null && !endpoint.isBlank()) {
                        builder.endpoint(endpoint);
                }

                // Для non-RU эндпоинтов SDK обменивает OAuth→IAM через российский IAM-сервер.
                // Поэтому при кастомном endpoint самостоятельно меняем OAuth на IAM токен.
                if (endpoint != null && !endpoint.isBlank()) {
                        String iamEndpoint = "iam." + endpoint;
                        String token = resolveToken(oauthToken);
                        if (token != null && !token.isBlank()) {
                                String iamToken = exchangeOauthToIam(token, iamEndpoint);
                                if (iamToken != null) {
                                        builder.credentialProvider(Auth.iamTokenBuilder().token(iamToken).build());
                                        return builder.build();
                                }
                        }
                        // fallback: пробуем с OAuth напрямую
                }

                // Стандартный путь: OAuth через встроенный SDK механизм
                String token = resolveToken(oauthToken);
                if (token != null && !token.isBlank()) {
                        builder.credentialProvider(Auth.oauthTokenBuilder().oauth(token).build());
                } else {
                        builder.credentialProvider(Auth.oauthTokenBuilder().fromEnv("OAUTH_TOKEN"));
                }
                return builder.build();
        }

        /** Возвращает OAuth-токен: из параметра или из переменной окружения. */
        private static String resolveToken(String oauthToken) {
                if (oauthToken != null && !oauthToken.isBlank())
                        return oauthToken;
                return System.getenv("OAUTH_TOKEN");
        }

        /**
         * Обменивает OAuth-токен на IAM-токен через указанный IAM-эндпоинт.
         * 
         * @param oauth   OAuth-токен
         * @param iamHost например "iam.api.yandexcloud.kz"
         * @return IAM токен или null при ошибке
         */
        @SuppressWarnings("unchecked")
        static String exchangeOauthToIam(String oauth, String iamHost) {
                try {
                        URL url = new URL("https://" + iamHost + "/iam/v1/tokens");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setDoOutput(true);
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);

                        String body = "{\"yandexPassportOauthToken\":\"" + oauth + "\"}";
                        try (OutputStream os = conn.getOutputStream()) {
                                os.write(body.getBytes(StandardCharsets.UTF_8));
                        }

                        if (conn.getResponseCode() == 200) {
                                ObjectMapper mapper = new ObjectMapper();
                                Map<?, ?> resp = mapper.readValue(conn.getInputStream(), Map.class);
                                return (String) resp.get("iamToken");
                        } else {
                                byte[] err = conn.getErrorStream() != null
                                                ? conn.getErrorStream().readAllBytes()
                                                : new byte[0];
                                System.err.println("[AuthService] IAM exchange failed (" + conn.getResponseCode()
                                                + "): " + new String(err, StandardCharsets.UTF_8));
                        }
                } catch (Exception e) {
                        System.err.println("[AuthService] IAM exchange error: " + e.getMessage());
                }
                return null;
        }

        public ServiceFactory getFactory() {
                return factory;
        }

        public OperationServiceGrpc.OperationServiceBlockingStub getOperationService() {
                return operationService;
        }

        public InstanceServiceGrpc.InstanceServiceBlockingStub getInstanceService() {
                return instanceService;
        }

        public ImageServiceGrpc.ImageServiceBlockingStub getImageService() {
                return imageService;
        }
}
