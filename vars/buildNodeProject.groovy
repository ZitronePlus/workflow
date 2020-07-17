def call(Map pipelineParams = [:]) {
  
    def commitMessage = "";

    pipeline {

        agent any

        options { buildDiscarder(logRotator(numToKeepStr: '5')) }

        parameters {
            choice(choices: ['off', 'qa'],
                   description: 'Trigger deployment to the selected environment. Requires a /helm/project.yaml with deployment description!',
                   name: 'DEPLOY_APPLICATION')
            booleanParam(defaultValue: false,
                         description: 'Trigger release build. Only possible for MASTER, creates a TAG, update the SNAPSHOT version',
                         name: 'RELEASE_APPLICATION')

        }
        environment {
            DEPLOY_APPLICATION = getDeployTarget(pipelineParams);
        }
       stages {
            stage('Setup') {
                steps {
                    script {
                        logger.banner(STAGE_NAME)
                        commitMessage = mavenUtil.getLastGitComment();
                        def pom = readMavenPom(file: 'pom.xml');
                        logger.info("DEPLOY_APPLICATION: $env.DEPLOY_APPLICATION");
                        logger.info("RELEASE_APPLICATION: $params.RELEASE_APPLICATION");

                        mavenUtil.clean();

                        if ('V3' == pipelineParams.get('helmVersion')) {
                            env.DOCKER_OC_IMAGE = 'itoah-docker-registry.ops.server.lan/moc/moc-icaas-tools:4.0.0'
                            env.HELM_UPGRADE_TIMEOUT = '240s'
                        } else {
                            env.HELM_UPGRADE_TIMEOUT = '240'
                        }
                    }
                }
            }
       }
    }
  def getDeployTarget(Map pipelineParams) {
    if (pipelineParams.containsKey('deployApplication') && env.DEPLOY_APPLICATION == 'off')
        return pipelineParams.get('deployApplication');
    else if (env.DEPLOY_APPLICATION == "null")
        return "off"

    return env.DEPLOY_APPLICATION
}
