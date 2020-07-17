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
            DEPLOY_APPLICATION = getDeployTarget(pipelineParams)
            registry = "docker_hub_account/lemtron"           
            dockerImage = ''
        }
        stages {
            stage('Setup') {
                steps {
                    script {
                        //logger.banner(STAGE_NAME)
                        commitMessage = "INITIAL"//debianUtil.getLastGitComment();
                        //logger.info("DEPLOY_APPLICATION: $env.DEPLOY_APPLICATION");
                        //logger.info("RELEASE_APPLICATION: $params.RELEASE_APPLICATION");                        
                    }
                }
            }           
            stage('Build Docker Image') {
                steps {                   
                       
                         //git 'https://github.com/ZitronePlus/MOC.git'
                        //logger.banner(STAGE_NAME)
                        //logger.info('Docker image cleanup before release') 
                        docker.withRegistry( 'https://registry.hub.docker.com', 'dockerHub' ) {
                        dockerImage = docker.build("lemtron/MOC")
                        }
                   
                }
            }
            stage('Push docker image') {
                steps {
                    script {
                         echo (STAGE_NAME)
                        docker.withRegistry( 'https://registry.hub.docker.com', 'dockerHub' ) {
                            dockerImage.push()
                         }
                        //logger.banner(STAGE_NAME)                   
                    }
                }
            }
        }
        post {
            always {
                script {
                    //logger.info('CLEANUP: Stop and remove local docker container. Timeout is set to 10 seconds.')
                    try {
                        timeout(time: 10, unit: 'SECONDS') {
                            //mavenUtil.removeDocker();
                        }
                    } catch (err) {
                        //logger.info('Caught Exception: ${err}')
                        currentBuild.result = "SUCCESS"
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
