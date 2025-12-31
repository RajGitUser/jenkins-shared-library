// call is the default function name
def call (Map configMap){
    pipeline {
    // These are pre-build sections
        agent {
            node {
                label 'AGENT-1'
            }
        }
        environment {
            COURSE = "Jenkins"
            appVersion = ""
            ACC_ID = "848332098195"
            PROJECT = configMap.get("project")
            COMPONENT = configMap.get("component")
        }
        options {
            timeout(time: 10, unit: 'MINUTES') 
            disableConcurrentBuilds()
        }
        // This is build section
        stages {
            stage('Read Version') {
                steps {
                    script{
                        def pom = readMavenPom file: 'pom.xml'
                        appVersion = pom.version
                        echo "app version: ${appVersion}"
                    }
                }
            }
            stage('Install Dependencies') {
                steps {
                    script{
                        sh """
                            mvn clean package
                        """
                    }
                }
            }
            stage('Unit Test') {
                steps {
                    script{
                        sh """
                            echo test
                        """
                    }
                }
            }
            //Here you need to select scanner tool and send the analysis to server
            /* stage('Sonar Scan'){
                environment {
                    def scannerHome = tool 'sonar-8.0'
                }
                steps {
                    script{
                        withSonarQubeEnv('sonar-server') {
                            sh  "${scannerHome}/bin/sonar-scanner"
                        }
                    }
                }
            }
            stage('Quality Gate') {
                steps {
                    timeout(time: 1, unit: 'HOURS') {
                        // Wait for the quality gate status
                        // abortPipeline: true will fail the Jenkins job if the quality gate is 'FAILED'
                        waitForQualityGate abortPipeline: true 
                    }
                }
            } */
            // stage('Dependabot Security Gate') {
            //     when {
            //         expression { false }
            //     }
            //     environment {
            //         GITHUB_OWNER = 'daws-86s'
            //         GITHUB_REPO  = 'catalogue'
            //         GITHUB_API   = 'https://api.github.com'
            //         GITHUB_TOKEN = credentials('GITHUB_TOKEN')
            //     }

            // }

            stage('Build Image') {
                steps {
                    script{
                        withAWS(region:'us-east-1',credentials:'aws-cred') {
                            sh """
                                aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com
                                docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion} .
                                docker images
                                docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                            """
                        }
                    }
                }
            }
            
            /* stage('Trivy Scan'){
                steps {
                    script{
                        sh """
                            trivy image \
                            --scanners vuln \
                            --severity HIGH,CRITICAL,MEDIUM \
                            --pkg-types os \
                            --exit-code 1 \
                            --format table \
                            ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${appVersion}
                        """
                    }
                }
            } */

            stage('Trigger DEV Deploy') {
                steps {
                    script {
                        build job: "../${COMPONENT}-deploy",
                            wait: false, // Wait for completion
                            propagate: false, // Propagate status
                            parameters: [
                                string(name: 'appVersion', value: "${appVersion}"),
                                string(name: 'deploy_to', value: "dev")
                            ]
                    }
                }
            }

        }

            

        post{
            always{
                echo 'I will always say Hello again!'
                cleanWs()
            }
            success {
                echo 'I will run if success'
            }
            failure {
                echo 'I will run if failure'
            }
            aborted {
                echo 'pipeline is aborted'
            }
        }
    }
}