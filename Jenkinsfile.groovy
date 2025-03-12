pipeline {
  environment { 
    WORKSHOP_NAME = 'ThanaphonSrib-Capco'
  }
  agent {
    kubernetes {
      yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: dind-agent
spec:
  containers:
  - name: dind
    image: docker:19.03.11-dind
    imagePullPolicy: Always
    tty: true
    securityContext:
      privileged: true
    volumeMounts:
      - name: docker-graph-storage
        mountPath: /var/lib/docker
  - name: kubectl
    image: bitnami/kubectl:1.30.4
    command:
    - cat
    tty: true
    securityContext:
      runAsUser: 1000
  volumes:
    - name: docker-graph-storage
      emptyDir: {}
  serviceAccount: jenkins
'''
      showRawYaml false
    }
  }
  stages {
    stage('Get Source Code') {
      steps {
        git url: 'https://github.com/ThanaphonSrib-Capco/jenkins-cicd-demo-workshop', branch: 'main'
      }
    }
    stage('Build Source Code') {
      steps {
        echo "Create html folder..."
        sh 'mkdir html'
        echo "Copy html and image into html folder..."
        sh 'cp index.html hero.png html'
      }
    }
    stage('Automate Test') {
      steps {
        echo "Run test..."
        junit 'test-result-mock.xml'
      }
    }
    stage('Build & Push Container') {
      steps {
        container('dind') {
            withCredentials([string(credentialsId: 'harbor-registry-hostname', variable: 'registry')]) {
            echo "Build container..."
            sh '''
            docker build -t ${WORKSHOP_NAME}/landing-page .
            echo "Push container..."
            docker tag ${WORKSHOP_NAME}/landing-page ${registry}/aep/${WORKSHOP_NAME}:${BUILD_NUMBER}
            '''
                withCredentials([usernamePassword(credentialsId: 'docker-credential', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    echo "Login to harbor..."
                    sh '''
                        docker login -u ${USERNAME} -p ${PASSWORD} ${registry}
                        echo "Push container..."
                        docker push ${registry}/aep/${WORKSHOP_NAME}:${BUILD_NUMBER}
                    '''
                }
            }
        }
      }
    }
    stage('Deploy to Kubernetes') {
      steps {
            withCredentials([string(credentialsId: 'harbor-registry-hostname', variable: 'registry')]) {
            echo "Replace image in deployment file..."
            sh '''
            sed -i "s|###REGISTRY_HOSTNAME###|${registry}|g" deploy.yaml
            sed -i "s|###WORKSHOP_NAME###|${WORKSHOP_NAME}|g" deploy.yaml
            sed -i "s|###BUILD_NUMBER###|${BUILD_NUMBER}|g" deploy.yaml
            cat deploy.yaml
            '''
            echo "Deploy to kubernetes..."
            container('kubectl') {
            sh '''
                kubectl apply -f deploy.yaml
            '''
            }
        }
      }
    }
    stage('Review Result') {
      steps {
        sh '''
            set +x  # Disable command printing
            WORKSHOP_RESULT=$(echo "${JENKINS_URL}" | sed "s|jenkins|${WORKSHOP_NAME}|")
            echo "Review result at: ${WORKSHOP_RESULT}"
        '''
        }
    }
  }
}
