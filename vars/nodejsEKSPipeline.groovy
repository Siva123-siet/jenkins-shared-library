def call(Map configMap){
    pipeline {
        // Pre-build
        agent {
            label 'AGENT-1'
        }
        environment { 
            appVersion = ''
            REGION = "us-east-1"
            ACC_ID = "097003440739"
            PROJECT = configMap.get('project')
            COMPONENT = configMap.get('component')
        }
        options {
            timeout(time: 30, unit: 'MINUTES') 
            disableConcurrentBuilds() 
        }
        parameters {
            booleanParam(name: 'deploy', defaultValue: false, description: 'Toggle this value')
        }
        // Build 
        stages {
            stage('Read package.json') {
                steps {
                    script {
                        // Read the package.json file
                        def packageJson = readJSON file: 'package.json'
                        // Access the version field
                        appVersion = packageJson.version
                        echo "Package version: ${appVersion}"
                    }
                }
            }
            stage('Install dependencies') {
                steps {
                    script {
                        sh """
                            npm install
                        """
                    }
                }
            }
            stage('Unit Testing') {
                steps {
                    script {
                        sh """
                            echo "unit tests"
                        """
                    }
                }
            }
            // stage('Sonar Scan') {
            //     environment {
            //     scannerHome = tool 'sonar-7.2'
            //     }
            //     steps {
            //      // Sonar Server environment
            //      withSonarQubeEnv('sonar-7.2') { // ID from System configuration
            //      sh "${scannerHome}/bin/sonar-scanner" // This command will run sonarqube scanner based on sonar-project.properties instaructions and give results to sonarqube server
            //    }
            //   }
            // }
            // Enable webhook in sonarqube server
            // stage("Quality Gate") {
            //     steps {
            //         timeout(time: 1, unit: 'HOURS') {
            //         waitForQualityGate abortPipeline: true }
            //     }
            // }
        //     stage('Check Dependabot Alerts') {
        //     environment { 
        //         GITHUB_TOKEN = credentials('github-token')
        //     }
        //     steps {
        //         script {
        //             // Fetch alerts from GitHub
        //             def response = sh(
        //                 script: """
        //                 curl -L \
        //                     -H "Accept: application/vnd.github+json" \
        //                     -H "Authorization: token ${GITHUB_TOKEN}" \
        //                     https://api.github.com/repos/Siva123-siet/${COMPONENT}/dependabot/alerts
        //                 """,
        //                 returnStdout: true
        //             ).trim()

        //             // Parse JSON
        //             def json = readJSON text: response

        //             // Filter alerts by severity
        //             def criticalOrHigh = json.findAll { alert ->
        //                 def severity = alert?.security_advisory?.severity?.toLowerCase()
        //                 def state = alert?.state?.toLowerCase()
        //                 return (state == "open" && (severity == "critical" || severity == "high"))
        //             }

        //             if (criticalOrHigh.size() > 0) {
        //                 error "❌ Found ${criticalOrHigh.size()} HIGH/CRITICAL Dependabot alerts. Failing pipeline!"
        //             } else {
        //                 echo "✅ No HIGH/CRITICAL Dependabot alerts found."
        //             }
        //         }
        //     }
        // }
            stage('Docker Build') {
                steps {
                    script {
                    withAWS(credentials: 'aws-auth', region: 'us-east-1') {
                    // Your AWS CLI commands or other AWS interactions go here
                    sh """
                    aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                    docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                    docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                    aws ecr wait image-scan-complete --repository-name ${PROJECT}/${COMPONENT} --image-id imageTag=${appVersion} --region ${REGION}
                    """
                    }
                    }
                }
            }
            stage('Check Scan Results') {
                steps {
                    script {
                        withAWS(credentials: 'aws-auth', region: 'us-east-1') {
                        // Fetch scan findings
                            def findings = sh(
                                script: """
                                    aws ecr describe-image-scan-findings \
                                    --repository-name ${PROJECT}/${COMPONENT} \
                                    --image-id imageTag=${appVersion} \
                                    --region ${REGION} \
                                    --output json
                                """,
                                returnStdout: true
                            ).trim()

                            // Parse JSON
                            def json = readJSON text: findings

                            def highCritical = json.imageScanFindings.findings.findAll {
                                it.severity == "HIGH" || it.severity == "CRITICAL"
                            }

                            if (highCritical.size() > 0) {
                                echo "❌ Found ${highCritical.size()} HIGH/CRITICAL vulnerabilities!"
                                currentBuild.result = 'FAILURE'
                                error("Build failed due to vulnerabilities")
                            } else {
                                echo "✅ No HIGH/CRITICAL vulnerabilities found."
                            }
                        }
                    }
                }
            }
            stage('Trigger deploy') {
                when {
                        expression { params.deploy }
                    }
                steps {
                    script {
                        // build job: 'catalogue-cd',
                        build job: "../${COMPONENT}-cd",
                        parameters: [
                        string(name: 'appVersion', value: "${appVersion}"),
                        string(name: 'deploy_to', value: 'dev')
                        ],
                        propagate: false, //even sg fails vpc will not be effected
                        wait: false // vpc will not wait for sg pipeline completion
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