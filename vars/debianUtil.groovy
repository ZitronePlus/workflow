def call(body) {
    def dockerRegistry = (env.DOCKER_REGISTRY) ?: 'https://hub.docker.com';
    def dockerImage = (env.DOCKER_OX_NODE_IMAGE) ?: dockerRegistry + '/_/debian:stretch-slim';
    def proxyUrl = proxyArgs.proxyUrl();

    //Login and download the image
    withDockerRegistry(registry: [url: 'https://' + dockerRegistry, credentialsId: 'jenkins-tooluser']) {
        sh("docker pull ${dockerImage}");
    }

    withDockerContainer(image: dockerImage) {
        body();
    }
}

/* Use dpkg-source and dpkg-buildpackage to execute a complex package build */
def buildDebianPackage(String buildFolder, String finalName){
    debianUtil{
        dir(buildFolder){
            sh "dpkg-source -Zgzip -b $finalName"
            dir(finalName) {
                sh 'dpkg-buildpackage -j2 -us -uc -b'
            }
        }
    }
}

/* Use dbkp --build to generate a lightweight archive with control file */
def buildDebianArchive(String buildFolder, String finalName) {
    debianUtil {
        dir(buildFolder) {
            sh 'if [ ! -d "./DEBIAN" ]; then echo "./DEBIAN folder not found!"; fi'
            sh 'chmod 755 ./DEBIAN'
            sh "find . -type f ! -regex '.*DEBIAN/.*' -printf '%P\\0' | xargs -r0 md5sum > './DEBIAN/md5sums'"
            sh "dpkg -b . ../$finalName"
        }
    }
}

def uploadDebs(String sourceFolder, String packageName, String repoName = 'mw_dev') {
    if (env.OX_DEBIAN_REPO) {
        def repository = env.OX_DEBIAN_REPO;

        debianUtil {
            withCredentials([usernameColonPassword(credentialsId: 'ox-debian-repo', variable: 'USERPASS')]) {
                dir(sourceFolder) {
                    def files = findFiles(glob: '*.deb')
                    files.each {
                        logger.info("Going to add package: $it")
                        //Delete before upload to force overwrite
                        //sh("curl -X DELETE -k -u $USERPASS $repository/repos/$repoName/file/$repoName/$it.name")

                        //Upload via aptly rest API, https://www.aptly.info/doc/api/files/
                        sh("curl -k -u $USERPASS -F file='@$it' $repository/files/$repoName")
                        //https://www.aptly.info/doc/api/repos/
                        sh("curl -X POST -k -u $USERPASS $repository/repos/$repoName/file/$repoName/$it.name?forceReplace=1")
                    }

                    //List packages
                    logger.info("List current files of the current package")
                    sh("curl -k -u $USERPASS $repository/repos/$repoName/packages?q='Name%20(~%20$packageName)' | json_pp")

                    //Publish
                    logger.info("Going to publish repo changes")
                    sh("curl -X PUT -H 'Content-Type: application/json' -u $USERPASS -k --data '{\"ForceOverwrite\":true,\"Signing\":{\"Skip\":true}}' $repository/publish/:./$repoName")
                }
            }
        }
    } else {
        logger.warn('No env.OX_DEBIAN_REPO defined, skip upload');
    }
}

def removeDocker() {
    logger.info('New: Stop Docker container and then remove the Image')
    execute("docker:stop docker:remove", "-Pdocker -DskipTests -Ddocker.host.fqdn=$env.NODE_NAME")
}

def buildDocker() {
    logger.info('New: Start Docker container and then build the Image')
    execute("docker:build", "-Pdocker -DskipTests -Ddocker.host.fqdn=$env.NODE_NAME")
}

def pushDocker() {
    execute("docker:push", "-Pdocker -DskipTests -Ddocker.host.fqdn=$env.NODE_NAME")
}

def getLastGitComment() {
    debianUtil{
        return sh (script: "git log -1 --pretty=%B", returnStdout: true).trim().toLowerCase();
    }
}
