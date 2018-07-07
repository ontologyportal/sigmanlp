#July 3, 208:  Revision to include SIGMA NLP
FROM centos:latest
MAINTAINER Anonymous Contributor

# Install a bunch of software we need
RUN yum install -y  \
    git \
    sudo \
    unzip \
    wget \
# Install make and gcc
    make \
    gcc \
# Handle sup. packages
    graphviz \
    ant; yum clean all

# Add the sumo user and add to wheel (i.e., make sudoer no pwd)
RUN useradd sumo && \
    usermod -aG wheel sumo && \
    sed -i 's/%wheel/# %wheel/' /etc/sudoers && \
    echo "%wheel     ALL=(ALL)     NOPASSWD: ALL" | tee -a /etc/sudoers

# Add java to the image
COPY jdk-8u171-linux-x64.rpm .
RUN rpm -ivh jdk-8u171-linux-x64.rpm && rm jdk-8u171-linux-x64.rpm

# Copy in the BASHRC
COPY bashrc .
RUN  cp bashrc /home/sumo/.bashrc && \
     chown sumo:sumo /home/sumo/.bashrc

# Copy in the sigmastart.sh script
COPY sigmastart.sh .
RUN chmod u+x sigmastart.sh

#Copy bashrc into the image and create a bunch of required directories
RUN su -l sumo -c "mkdir /home/sumo/workspace; mkdir /home/sumo/Programs/; mkdir /home/sumo/Programs/bins" && \
    su -l sumo -c "cd /home/sumo/Programs; wget http://mirrors.advancedhosters.com/apache/tomcat/tomcat-9/v9.0.10/bin/apache-tomcat-9.0.10.zip" && \
    su -l sumo -c "cd /home/sumo/Programs; wget http://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz" && \
    su -l sumo -c "cd /home/sumo/Programs; wget http://wwwlehre.dhbw-stuttgart.de/~sschulz/WORK/E_DOWNLOAD/V_2.0/E.tgz" && \
    su -l sumo -c "cd /home/sumo/Programs; tar -xvzf E.tgz" && \
    su -l sumo -c "cd /home/sumo/Programs; unzip apache-tomcat-9.0.10.zip" && \
    su -l sumo -c "cd /home/sumo/Programs; mv E.tgz bins; mv apache-tomcat-9.0.10.zip bins" && \
    su -l sumo -c "cd /home/sumo/Programs/apache-tomcat-9.0.10/bin/; chmod 777 *" && \
    su -l sumo -c "cd /home/sumo/workspace; git clone https://github.com/ontologyportal/sigmakee; git clone https://github.com/ontologyportal/sumo" && \
    su -l sumo -c "cd /home/sumo; mkdir .sigmakee; cd .sigmakee; mkdir KBs; cp -R /home/sumo/workspace/sumo/* KBs; cp /home/sumo/workspace/sigmakee/config.xml /home/sumo/.sigmakee/KBs" && \
    su -l sumo -c "sed -i 's/theuser/sumo/g' /home/sumo/.sigmakee/KBs/config.xml" && \
    su -l sumo -c "sed -i 's/~/\/home\/sumo/g' /home/sumo/.sigmakee/KBs/config.xml" && \
    su -l sumo -c "cd /home/sumo/Programs; gunzip WordNet-3.0.tar.gz; tar -xvf WordNet-3.0.tar; cp WordNet-3.0/dict/* /home/sumo/.sigmakee/KBs/WordNetMappings/" && \
    su -l sumo -c "cd /home/sumo/Programs/E; ./configure; make; make install; cd /home/sumo" && \
    su -l sumo -c "source /home/sumo/.bashrc; cd /home/sumo/workspace/sigmakee; ant" && \
    #Let's now handle SIGMANLP: https://github.com/ontologyportal/sigmanlp
    su -l sumo -c "cd /home/sumo/workspace; git clone https://github.com/ontologyportal/sigmanlp" && \
    su -l sumo  -c "cd /home/sumo/Programs; wget 'http://nlp.stanford.edu/software/stanford-corenlp-full-2018-01-31.zip'; unzip stanford-corenlp-full-2018-01-31.zip; rm stanford-corenlp-full-2018-01-31.zip" && \
    su -l sumo -c "cd /home/sumo/Programs/stanford-corenlp-full-2018-01-31/; unzip stanford-corenlp-3.9.0-models.jar; cp /home/sumo/Programs/stanford-corenlp-full-2018-01-31/stanford-corenlp-3.9.0.jar /home/sumo/workspace/sigmanlp/lib; cp /home/sumo/Programs/stanford-corenlp-full-2018-01-31/stanford-corenlp-3.9.0-models.jar /home/sumo/workspace/sigmanlp/lib; cd /home/sumo/workspace/sigmanlp; ant; ant dist" && \
    #Handle the edits to config.xml
    su -l sumo -c "sed -i 's/<configuration >/<configuration>/g' /home/sumo/.sigmakee/KBs/config.xml" && \
    su -l sumo -c "sed -i 's/<configuration>/<configuration>\n  <preference name=\"dbUser\" value=\"sa\"\/>/g' /home/sumo/.sigmakee/KBs/config.xml" && \
    su -l sumo -c "sed -i 's/<configuration>/<configuration>\n  <preference name=\"loadFresh\" value=\"false\"\/>/g' /home/sumo/.sigmakee/KBs/config.xml" && \
    #Create the DB (This is required)
    su -l sumo -c "java -Xmx7g -classpath /home/sumo/workspace/sigmakee/build/classes:/home/sumo/workspace/sigmakee/build/lib/*:/home/sumo/workspace/sigmakee/lib/* com.articulate.sigma.PasswordService -c" && \
    #Run the non-interactive account creation
    su -l sumo -c "java -Xmx7g -classpath /home/sumo/workspace/sigmakee/build/classes:/home/sumo/workspace/sigmakee/build/lib/*:/home/sumo/workspace/sigmakee/lib/* com.articulate.sigma.PasswordService -a3 admin admin nlpAdmin@nowhere.com" && \ 
    #The following is a cleanup - the problem is that you can't change the password once we do this.
    su -l sumo -c "rm -Rf /home/sumo/Programs/bins; rm -Rf /home/sumo/Programs/*.tar; rm -Rf /home/sumo/workspace/sigmakee; rm -Rf /home/sumo/workspace/sigmanlp"

