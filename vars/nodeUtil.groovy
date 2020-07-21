def call(body) {
    def dockerRegistry = (env.DOCKER_REGISTRY) ?: 'itoah-docker-registry.ops.server.lan';
    def dockerImage = (env.DOCKER_NODE_IMAGE) ?: dockerRegistry + '/lemtron/node:latest';
    def proxyUrl = proxyArgs.proxyUrl();

    //Login and download the image
    withDockerRegistry(registry: [url: 'https://' + dockerRegistry, credentialsId: 'jenkins-tooluser']) {
        sh("docker pull ${dockerImage}");
    }

    withDockerContainer(image: dockerImage) {
        // Init node.js
        sh "npm config set proxy $proxyUrl"
        sh "npm config set https-proxy $proxyUrl"

        body();
    }
}

def install(){
    nodeUtil{
        //Cleanup
        sh "rm -rf dist"
        sh "rm -rf dist@tmp"
        sh "rm -rf build"
        sh "rm -rf node_modules"

        sh "npm install --no-optional"
    }
}

def patchVersion() {
    nodeUtil{
        sh 'npm --no-git-tag-version version patch'
    }
}
