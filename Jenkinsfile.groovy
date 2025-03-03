pipeline {
  environment { 
    WORKSHOP_NAME = 'sarawukl'
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
  volumes:
    - name: docker-graph-storage
      emptyDir: {}
'''
      showRawYaml false
    }
  }
  stages {
    stage('Get Source Code') {
      steps {
        git url: 'https://github.com/sarawukl/jenkins-cicd-demo-workshop', branch: 'main'
      }
    }
    stage('Build Source Code') {
      steps {
        sh '''
        echo "Create html folder..."
        mkdir html
        echo "Copy html and image into html folder..."
        cp index.html hero.png html
        '''
      }
    }
    stage('Automate Test') {
      steps {
        sh '''
        echo "Run test..."
        '''
        junit 'test-result-mock.xml'
      }
    }
    stage('Build & Push Container') {
      steps {
        container('dind') {
            withCredentials([string(credentialsId: 'harbor-registry-hostname', variable: 'registry')]) {
            sh '''
            echo "Build container..."
            docker build -t ${WORKSHOP_NAME}/landing-page .
            echo "Push container..."
            docker tag ${WORKSHOP_NAME}/landing-page ${registry}/aep/${WORKSHOP_NAME}:${BUILD_NUMBER}
            '''
                withCredentials([usernamePassword(credentialsId: 'docker-credential', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh '''
                    echo "Login to harbor..."
                        docker login -u ${USERNAME} -p ${PASSWORD} ${registry}
                        echo "Push container..."
                        docker push ${registry}/aep/${WORKSHOP_NAME}:${BUILD_NUMBER}
                    '''
                }
            }
        }
      }
    }
  }
}
