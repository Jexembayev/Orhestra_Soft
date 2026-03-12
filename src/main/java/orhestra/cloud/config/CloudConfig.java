package orhestra.cloud.config;

public class CloudConfig {

    // AUTH
    public String oauthToken; // можно оставить null — AuthService берёт из ENV
    public String cloudId; // опционально
    public String folderId;
    public String zoneId;
    /** Переопределяет gRPC-эндпоинт SDK. null = дефолтный api.cloud.yandex.net */
    public String apiEndpoint; // напр.: "api.yandexcloud.kz" для Казахстана

    // NETWORK
    public String networkId; // опционально
    public String subnetId;
    public String securityGroupId;
    public boolean publicIp = true;

    // VM
    public int vmCount;
    public String vmName;
    public String imageId;
    public String platformId;
    public int cpu;
    public int ramGb;
    public int diskGb;

    // SSH
    public String sshUser;
    public String sshPublicKey;

    // OVPN (новое)
    /** Явный id инстанса с OpenVPN Access Server (опционально). */
    public String ovpnInstanceId;
    /**
     * Альтернатива: тег (label), по которому будем искать OVPN ВМ (опционально).
     */
    public String ovpnTag;

    // S3 / Object Storage (Yandex, MinIO, AWS — любой S3-совместимый)
    /** Эндпоинт S3: https://storage.yandexcloud.net или http://localhost:9000 */
    public String s3Endpoint;
    /** Имя бакета по умолчанию */
    public String s3Bucket;
    /** Access Key ID (статический ключ сервисного аккаунта) */
    public String s3AccessKeyId;
    /** Secret Access Key */
    public String s3SecretAccessKey;

    public String oauthToken() {
        return oauthToken;
    }

    public String cloudId() {
        return cloudId;
    }

    public String folderId() {
        return folderId;
    }

    public String zoneId() {
        return zoneId;
    }

    /**
     * Автоматически разрешает эндпоинт gRPC API:
     * - если apiEndpoint явно задан — используем его
     * - zone начинается с “kz” — авто: api.yandexcloud.kz
     * - иначе null (дефолтный русский endpoint)
     */
    public String resolveEndpoint() {
        if (apiEndpoint != null && !apiEndpoint.isBlank())
            return apiEndpoint;
        if (zoneId != null && zoneId.toLowerCase().startsWith("kz"))
            return "api.yandexcloud.kz";
        return null; // дефолт: api.cloud.yandex.net
    }

    public String networkId() {
        return networkId;
    }

    public String subnetId() {
        return subnetId;
    }

    public String securityGroupId() {
        return securityGroupId;
    }

    public boolean publicIp() {
        return publicIp;
    }

    public int vmCount() {
        return vmCount;
    }

    public String vmName() {
        return vmName;
    }

    public String imageId() {
        return imageId;
    }

    public String platformId() {
        return platformId;
    }

    public int cpu() {
        return cpu;
    }

    public int ramGb() {
        return ramGb;
    }

    public int diskGb() {
        return diskGb;
    }

    public String sshUser() {
        return sshUser;
    }

    public String sshPublicKey() {
        return sshPublicKey;
    }

    public String ovpnInstanceId() { return ovpnInstanceId; }
    public String ovpnTag()        { return ovpnTag; }

    public String s3Endpoint()         { return s3Endpoint; }
    public String s3Bucket()           { return s3Bucket; }
    public String s3AccessKeyId()      { return s3AccessKeyId; }
    public String s3SecretAccessKey()  { return s3SecretAccessKey; }

    public boolean hasS3Credentials() {
        return s3AccessKeyId != null && !s3AccessKeyId.isBlank();
    }

    @Override
    public String toString() {
        return "CloudConfig{" +
                "folderId='" + folderId + '\'' +
                ", zoneId='" + zoneId + '\'' +
                ", subnetId='" + subnetId + '\'' +
                ", vmCount=" + vmCount +
                ", vmName='" + vmName + '\'' +
                '}';
    }
}
