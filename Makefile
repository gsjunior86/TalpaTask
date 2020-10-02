default: build

build:
	docker-compose -f docker-compose.yml down
	mvn clean install -U -DskipTests -Dmaven.javadoc.skip=true
	
push-images:
	docker push gsjunior/talpa

dockerize:
	docker build -f Dockerfile -t gsjunior/talpa .
