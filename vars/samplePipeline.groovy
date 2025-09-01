def call(Map configMap){
    pipeline {
        // Pre-build
        agent {
            label 'AGENT-1'
        }
        environment { 
            COURSE = 'jenkins'
            greeting = configMap.get('greeting')
        }
        options {
            timeout(time: 30, unit: 'MINUTES') 
            disableConcurrentBuilds() 
        }
        parameters {
            string(name: 'PERSON', defaultValue: 'Mr Jenkins', description: 'Who should I say hello to?')
            text(name: 'BIOGRAPHY', defaultValue: '', description: 'Enter some information about the person')
            booleanParam(name: 'TOGGLE', defaultValue: true, description: 'Toggle this value')
        }
        // Build 
        stages {
            stage('Build') {
                steps {
                    script{
                        // jenkins treats this as shell script
                        sh """
                            echo "Hello Build"
                            env
                            echo "Hello ${params.PERSON}" 
                        """
                    }
                }
            }
            stage('Test') {
                steps {
                    script{
                        echo 'Testing..'
                    }
                }
            }
        }
        //post build
        post { 
            always { 
                echo 'I will always say Hello again!'
                deleteDir()
            }
            success { 
                echo 'Hello Success'
            }
            failure { 
                echo 'Hello failure'
            }
        }
    }
}