package orhestra.cloud.auth;

import yandex.cloud.api.compute.v1.ImageServiceGrpc;
import yandex.cloud.api.compute.v1.InstanceServiceGrpc;
import yandex.cloud.api.operation.OperationServiceGrpc;
import yandex.cloud.sdk.ServiceFactory;
import yandex.cloud.sdk.auth.Auth;

import java.time.Duration;

public class AuthService {
        private final ServiceFactory factory;
        private final OperationServiceGrpc.OperationServiceBlockingStub operationService;
        private final InstanceServiceGrpc.InstanceServiceBlockingStub instanceService;
        private final ImageServiceGrpc.ImageServiceBlockingStub imageService;

        /** Create using OAUTH_TOKEN from environment variable. */
        public AuthService() {
                this((String) null);
        }

        /** Create using explicit OAuth token (falls back to ENV if null). */
        public AuthService(String oauthToken) {
                this.factory = buildFactory(oauthToken);

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

        private static ServiceFactory buildFactory(String oauthToken) {
                var builder = ServiceFactory.builder()
                                .requestTimeout(Duration.ofMinutes(1));

                if (oauthToken != null && !oauthToken.isBlank()) {
                        builder.credentialProvider(Auth.oauthTokenBuilder().oauth(oauthToken).build());
                } else {
                        builder.credentialProvider(Auth.oauthTokenBuilder().fromEnv("OAUTH_TOKEN"));
                }

                return builder.build();
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
