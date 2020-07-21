def call(Map config=[:], Closure body) {
    node {
        git url: "https://github.com/werne2j/sample-nodejs"        
        
        
        stage("Clean") {
            sh "gulp clean"
            sh "npm clean"
        }        
        
        stage("Install") {
            sh "npm install"
        } 
        
       
        stage("Test") {
            sh "npm test"
        }   
        
        stage("Deploy") {
            if (config.deploy) {
                sh "npm publish"
            }
        }        
        
        body()
    }
}
