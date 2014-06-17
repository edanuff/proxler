package proxler;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.DockerException;
import com.github.dockerjava.client.model.Container;
import com.github.dockerjava.client.model.Container.Port;

public class ContainerManager {

    private final Logger logger = LoggerFactory
            .getLogger(ContainerManager.class);

    DockerClient dockerClient;
    URI dockerUri;

    public class ContainerInfo {
        String host;
        int port;

        ContainerInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    static ContainerManager INSTANCE = new ContainerManager();

    public ContainerManager() {
        String url = System.getProperty("docker.url",
                "http://192.168.59.103:2375");
        try {
            dockerUri = new URI(url);
        } catch (URISyntaxException e1) {
            logger.error("Could not initialize Docker url", e1);
        }
        try {
            dockerClient = new DockerClient(url);
        } catch (DockerException e) {
            logger.error("Could not initialize Docker client", e);
        }
    }

    public Container findContainerByTag(String tag) {
        if (tag == null) {
            return null;
        }
        List<Container> containers = dockerClient.listContainersCmd().exec();
        if (containers == null) {
            return null;
        }
        for (Container container : containers) {
            if (container.getImage().startsWith(tag + ":latest")) {
                return container;
            }
        }
        return null;
    }

    public ContainerInfo getContainerLocation(String id) {
        Container container = findContainerByTag(id);
        if (container == null) {
            return null;
        }
        logger.info("Found container: " + container.getId());

        int container_port = 0;
        try {
            Port cport = container.getPorts()[0];
            container_port = cport.getPublicPort();

        } catch (Exception e) {
            logger.error("Error getting container port", e);
            return null;
        }

        String localhost = dockerUri.getHost();
        logger.info(localhost + ":" + container_port);

        return new ContainerInfo(localhost, container_port);
    }

}
