package br.com.ricarte.assinaflow.testutil;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

/** Uses the official unshaded transport so compatible HttpCore maintenance can be resolved normally. */
public class PatchedDockerClientProviderStrategy extends DockerClientProviderStrategy {
    private final DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withApiVersion(System.getProperty("api.version", "1.44")).build();
    private DockerClient client;

    @Override public TransportConfig getTransportConfig() {
        return TransportConfig.builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
    }
    @Override public synchronized DockerClient getDockerClient() {
        if (client == null) client = DockerClientImpl.getInstance(config,
                new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build());
        return client;
    }
    @Override public String getDescription() { return "configured Docker host with maintained HttpCore transport"; }
    @Override protected int getPriority() { return 1000; }
    @Override protected boolean isApplicable() { return true; }
    @Override protected boolean isPersistable() { return false; }
}
