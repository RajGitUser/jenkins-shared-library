def call (Map configMap) {
    pipeline {
        agent {
            node {
                label 'AGENT-1'
            }
        }

        environment {
            ACC_ID    = "848332098195"
            PROJECT   = configMap.get("project")
            COMPONENT = configMap.get("component")
        }

        options {
            timeout(time: 10, unit: 'MINUTES')
            disableConcurrentBuilds()
        }

        stages {

            stage('Read Version') {
                steps {
                    script {
                        def packageJSON = readJSON file: 'package.json'
                        def appVersion  = packageJSON.version
                        env.APP_VERSION = appVersion   // make it globally available
                        echo "App Version: ${env.APP_VERSION}"
                    }
                }
            }

            stage('Install Dependencies') {
                steps {
                    sh 'npm install'
                }
            }

            stage('Unit Test') {
                steps {
                    sh 'npm test'
                }
            }

            // stage('Sonar Scan') {
            //         steps {
            //             catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
            //             script {
            //                 def scannerHome = tool 'sonar-scanner'
            //                 withSonarQubeEnv('sonar-server') {
            //                     sh """
            //                         ${scannerHome}/bin/sonar-scanner \
            //                         -Dsonar.projectKey=catalogue \
            //                         -Dsonar.sources=.
            //                     """
            //                 }
            //             }
            //         }
            //     }
            // }

            stage('Build Image') {
                steps {
                    script {
                        withAWS(region: 'us-east-1', credentials: 'aws-cred') {
                            sh """
                            aws ecr get-login-password --region us-east-1 \
                            | docker login --username AWS --password-stdin ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com

                            docker build -t ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${env.APP_VERSION} .
                            docker push ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${env.APP_VERSION}
                            """
                        }
                    }
                }
            }
            /* // stage('Trivy Scan') {
            //     steps {
            //         script {
            //             sh """
            //                 trivy image \
            //                 --scanners vuln \
            //                 --severity HIGH,CRITICAL,MEDIUM \
            //                 --pkg-types os \
            //                 --exit-code 1 \
            //                 --format table \
            //                 ${ACC_ID}.dkr.ecr.us-east-1.amazonaws.com/${PROJECT}/${COMPONENT}:${env.APP_VERSION}
            //             """
            //         }
            //     }
            // } */

            stage('Trigger DEV Deploy') {
                steps {
                    script {
                        build job: '../catalogue-deploy',
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

        post {
            always {
                echo 'Cleaning workspace'
                cleanWs()
            }
            success {
                echo 'Pipeline succeeded'
            }
            failure {
                echo 'Pipeline failed'
            }
            aborted {
                echo 'Pipeline aborted'
            }
        }
    }


}