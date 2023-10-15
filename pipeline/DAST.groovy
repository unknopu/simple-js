pipeline {
    agent { label 'linux' }
    environment {
        ARTIFACT_TAG = "latest"
    }
    
    stages {
        stage('Prepare Env'){
            steps {
                cleanWs()
            }
        }
        stage('Checkout Code') {
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId:'gitibm11455425',  usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]){
                    git branch: 'master',
                    url: "https://$USERNAME:$PASSWORD@${APP_CODE}"
                }
            }
        }
        stage('unit-test') {
            steps {
                script {
                    sh """
                        npm install
                        npm test -- --ci --reporters=default --reporters=jest-junit
                    """
                }
            }
            post {
                always {
                    archive "junit.xml"
                }
            }
        }
        stage('SAST'){
            environment {
                def scannerHome = tool 'sonarqube-scanner';
            }
            steps {
                script {
                    sh """
                        ${scannerHome}/bin/sonar-scanner \
                        -Dsonar.projectKey=ratings \
                        -Dsonar.projectName=ratings \
                        -Dsonar.sources=. \
                        -Dsonar.login=${SONAR_TOKEN} \
                        -Dsonar.host.url=${SONAR_REPO} \
                        -Dsonar.verbose=true \
                        -Dsonar.qualitygate.wait=false
                    """
                    sh """
                        /home/ubuntu/.nvm/versions/node/v18.18.0/bin/sonar-report \
                        --sonarurl=${SONAR_REPO} \
                        --sonartoken=${SONAR_TOKEN} \
                        --sonarcomponent=ratings \
                        --project=ratings \
                        --application=ratings \
                        --release="${env.BUILD_ID}" \
                        --branch=master \
                        --output=sonar-report.html    
                    """
                }
            }
            // post {
            //     always {
            //         archive "sonar-report.html"
            //     }
            // }
        }
        stage('Dependency Track') {
            environment {
                AUTH = credentials('dependency-track')
            }
            steps {
                sh """
                    /home/ubuntu/.nvm/versions/node/v18.18.0/bin/cyclonedx-npm \
                    --output-file cyclonedx-sbom-ratings.json package-lock.json

                    /home/ubuntu/.nvm/versions/node/v18.18.0/bin/npx viewbom \
                    cyclonedx-sbom-ratings.json cyclonedx-sbom-ratings.html

                    curl -X "POST" "http://13.212.236.98:8081/api/v1/bom" \
                    -H 'Content-Type: multipart/form-data' \
                    -H "X-Api-Key: ${AUTH}" \
                    -F "autoCreate=true" \
                    -F "projectName=ratings" \
                    -F "projectVersion=1" \
                    -F "bom=@cyclonedx-sbom-ratings.json"
                """
            }
            post {
                always {
                    archive "cyclonedx-sbom-ratings.json"
                }
            }
        }
        // stage('DefectDojo Engagement') {
        //     environment {
        //         def TODAY = java.time.LocalDate.now()
        //         def ENDDAY = TODAY.plusDays(1)
        //         DEFECTDOJO_PRODUCT_ID = 1
                
        //     }
        //     steps {
        //         script {
        //             def PAYLOAD = """
        //                 { \
        //                     "tags": ["jenkins"], \
        //                     "name": "ratings-master-${env.BUILD_ID}", \
        //                     "first_contacted": "${TODAY}", \
        //                     "target_start": "${TODAY}", \
        //                     "target_end": "${ENDDAY}", \
        //                     "version": "1.0.0", \
        //                     "engagement_type": "CI/CD", \
        //                     "build_id": "${env.BUILD_ID}", \
        //                     "product": "${DEFECTDOJO_PRODUCT_ID}" \
        //                 } 
        //             """
        //             sh """
        //                 curl -X POST "${DEFECTDOJO_URL}/api/v2/engagements/" \
        //                 --header "Authorization: Token ${DEFECTDOJO_API_KEY}" \
        //                 --header "Content-Type:application/json" -d '${PAYLOAD}'

        //                 curl -X POST "${DEFECTDOJO_URL}/api/v2/import-scan/" \
        //                 --header "Content-Type:multipart/form-data" \
        //                 --header "Authorization: Token ${DEFECTDOJO_API_KEY}" \
        //                 --form product_name="ratings" \
        //                 --form engagement_name="ratings-master-${env.BUILD_ID}" \
        //                 --form environment="master" \
        //                 --form close_old_findings="true" \
        //                 --form close_old_findings_product_scope="true" \
        //                 --form minimum_severity="Info" \
        //                 --form service="ratings" \
        //                 --form file=@"sonar-report.html" \
        //                 --form scan_type="SonarQube Scan" \
        //                 --form test_title="SonarQube Scan"
        //             """
                    
        //         }
        //     }
        // }
        stage('Build Artifact') {
            steps {
                script {
                    sh """
                        docker build --file builder/distroless.yaml -t share/ratings .
                        docker tag share/ratings:latest ${REGISTRY_URL}:latest
                    """
                }
            }
        }
        stage('Image Scan') {
            steps {
                script {
                    sh """
                        aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 290913681714.dkr.ecr.ap-southeast-1.amazonaws.com

                        crane pull ${REGISTRY_URL}:latest image.tar
                        trivy image --exit-code 0 \
                        --scanners vuln,config,secret \
                        --format json -o trivy-image.json \
                        --input image.tar

                        trivy image --exit-code 0\
                        --scanners vuln,config,secret \
                        --format template \
                        --template "@/home/ubuntu/temp/trivy/contrib/html.tpl" \
                        -o trivy-image.html --input image.tar
                    """
                }
            }
            post {
                always {
                    archive "trivy-image.json"
                    archive "trivy-image.html"
                }
            }
        }
        stage('Push Artifact') {
            steps {
                script {
                    sh """
                        aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 290913681714.dkr.ecr.ap-southeast-1.amazonaws.com
                        docker push ${REGISTRY_URL}:latest 
                    """
                }
            }
        }
        // stage('Deploy') {
        //     steps {
        //         script {
        //             sh """
        //                 aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 290913681714.dkr.ecr.ap-southeast-1.amazonaws.com
        //             """
        //             ansiblePlaybook(
        //                 credentialsId: 'lin-pem', 
        //                 inventory: 'iaac/hosts/host-linux.ini', 
        //                 playbook: 'iaac/playbook-ratings.yml', 
        //                 disableHostKeyChecking: true
        //             )
        //         }
        //     }
        // }
        // stage('DAST') {
        //     steps {
        //         script {
        //             sh """
        //                 zap-api-scan.py -t "${ENV_URL}" \
        //                 -r ${ZAP_REPORT_PREFIX}-report.html \
        //                 -x ${ZAP_REPORT_PREFIX}-report.xml -f openapi -I
        //                 mkdir -p ${REPORT_DIR}
        //                 cp /zap/wrk/${ZAP_REPORT_PREFIX}-report.* ${REPORT_DIR}
        //             """
        //         }
        //     }
        // }
        stage('Complete Job') {
            steps {
                script {
                    sh """
                        docker logout
                    """
                }
            }
        }
    }
}