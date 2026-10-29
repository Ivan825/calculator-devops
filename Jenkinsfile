// Declarative CI/CD pipeline: Checkout -> Build -> Test -> Docker Build -> Push -> Deploy
//
// Jenkins prerequisites (see README "Jenkins setup"):
//   - Plugins: Pipeline, Git, GitHub, JUnit, Credentials Binding, Email Extension, Timestamper
//   - Credential "dockerhub-credentials" (Username with password; password = Docker Hub access token)
//   - Extended E-mail Notification configured with an SMTP server
pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()           // two deploys never race each other
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
        skipDefaultCheckout()               // done explicitly in the Checkout stage
    }

    triggers {
        githubPush()                        // GitHub webhook (needs a public Jenkins URL)
        pollSCM('* * * * *')                // fallback: poll GitHub every minute
    }

    environment {
        // Homebrew Jenkins on macOS starts with a minimal PATH; add Docker Desktop and Homebrew
        PATH         = "/usr/local/bin:/opt/homebrew/bin:${env.PATH}"
        DOCKER_IMAGE = 'vortexfrenzy/calculator-devops'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
        HOST_PORT    = '8081'
        NOTIFY_EMAIL = 'Ivan.Bhargava@iiitb.ac.in'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --pretty="Building commit %h by %an: %s"'
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B clean package -DskipTests'
                archiveArtifacts artifacts: 'target/calculator.jar', fingerprint: true
            }
        }

        stage('Test') {
            steps {
                // A failing test makes mvn exit non-zero, which fails this stage and the build
                sh './mvnw -B test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    docker build \
                        --build-arg APP_VERSION="$IMAGE_TAG" \
                        -t "$DOCKER_IMAGE:$IMAGE_TAG" \
                        -t "$DOCKER_IMAGE:latest" \
                        .
                '''
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                                                  usernameVariable: 'DOCKERHUB_USER',
                                                  passwordVariable: 'DOCKERHUB_TOKEN')]) {
                    sh '''
                        echo "$DOCKERHUB_TOKEN" | docker login -u "$DOCKERHUB_USER" --password-stdin
                        docker push "$DOCKER_IMAGE:$IMAGE_TAG"
                        docker push "$DOCKER_IMAGE:latest"
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                // Re-running is safe: compose only recreates containers whose image/config changed
                sh 'docker compose up -d --no-build --remove-orphans --wait --wait-timeout 180'
                sh '''
                    echo "Verifying deployment through Nginx on port $HOST_PORT"
                    for i in $(seq 1 30); do
                        if curl -fsS "http://localhost:$HOST_PORT/actuator/health" | grep -q '"status":"UP"'; then
                            echo "Health check passed"
                            curl -fsS "http://localhost:$HOST_PORT/api/v1/add?a=2&b=3"; echo
                            docker compose ps
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "Health check failed"
                    exit 1
                '''
            }
            post {
                failure {
                    sh 'docker compose ps; docker compose logs --tail=100 || true'
                }
            }
        }
    }

    post {
        always {
            // Keep the last 5 numbered image tags locally and drop dangling layers
            sh '''
                docker image ls "$DOCKER_IMAGE" --format '{{.Tag}}' | grep -E '^[0-9]+$' | while read -r tag; do
                    if [ "$tag" -lt $((IMAGE_TAG - 5)) ]; then
                        docker image rm "$DOCKER_IMAGE:$tag" >/dev/null 2>&1 || true
                    fi
                done
                docker image prune -f >/dev/null 2>&1 || true
            '''
            emailext(
                to: "${env.NOTIFY_EMAIL}",
                subject: "[Jenkins] ${env.JOB_NAME} #${env.BUILD_NUMBER}: ${currentBuild.currentResult}",
                mimeType: 'text/html',
                body: """
                    <h3>${env.JOB_NAME} #${env.BUILD_NUMBER} finished with status <b>${currentBuild.currentResult}</b></h3>
                    <p>Duration: ${currentBuild.durationString.replace(' and counting', '')}</p>
                    <p>Image: ${env.DOCKER_IMAGE}:${env.IMAGE_TAG}</p>
                    <p>Console output: <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></p>
                    <p>Test results: <a href="${env.BUILD_URL}testReport">${env.BUILD_URL}testReport</a></p>
                """
            )
        }
    }
}
