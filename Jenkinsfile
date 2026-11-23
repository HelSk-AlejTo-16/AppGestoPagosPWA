pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Compilación, Pruebas y Cobertura (JaCoCo)') {
            steps {
                sh 'chmod +x ./gradlew && ./gradlew --no-daemon test jacocoTestReport'
            }
        }

        stage('Análisis de Calidad y Seguridad (SonarQube)') {
            steps {
                withSonarQubeEnv('SonnarServer') {
                    sh './gradlew --no-daemon sonarqube -Dsonar.host.url=http://host.docker.internal:9000'
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
            archiveArtifacts artifacts: 'build/reports/jacoco/test/**, build/reports/tests/test/**', allowEmptyArchive: true
        }
    }
}
