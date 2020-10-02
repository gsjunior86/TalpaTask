#Download base image ubuntu 20.04
FROM ubuntu:20.04


# Install OpenJDK-8
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk && \
    apt-get install -y ant && \
    apt-get clean;
    
    
# Setup JAVA_HOME -- useful for docker commandline
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64/
RUN export JAVA_HOME

### 3. Get Python, PIP

RUN apt-get update \
  && apt-get install -y python3-pip wget python3-dev \
  && cd /usr/local/bin \
  && ln -s /usr/bin/python3 python \
  && pip3 install --upgrade pip


RUN pip install --upgrade cython
RUN pip install --upgrade pip

RUN pip install joblib
RUN pip install pandas
RUN pip install scikit-learn

ENV DAEMON_RUN=true
ENV SPARK_VERSION=3.0.1
ENV HADOOP_VERSION=2.7
ENV SCALA_VERSION=2.12.4
ENV SCALA_HOME=/usr/share/scala



RUN wget http://apache.mirror.iphh.net/spark/spark-${SPARK_VERSION}/spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz && tar -xvzf spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz \
      && mv spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION} spark \
      && rm spark-${SPARK_VERSION}-bin-hadoop${HADOOP_VERSION}.tgz 

 
RUN export PATH="/spark/bin:$PATH"
RUN export SPARK_HOME="/spark/"

COPY ./python/model.pkl /data/talpa/py/model.pkl
COPY ./python/Prediction.py /data/talpa/py/Prediction.py      
COPY ./target/TalpaTask-1.0.jar /data/talpa/TalpaTask-1.0.jar
COPY ./data/data_case_study.csv /data/talpa/data_case_study.csv
COPY ./target/lib /data/talpa/lib

WORKDIR /data/talpa

CMD /spark/bin/./spark-submit --class br.gsj.Main --jars $(echo lib/*.jar | tr ' ' ',') --driver-memory 5G --master local[*] TalpaTask-1.0.jar
