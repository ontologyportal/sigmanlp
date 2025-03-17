# Build status

![badge](https://github.com/ontologyportal/sigmanlp/actions/workflows/ant.yml/badge.svg)

# Test status

![badge](https://github.com/ontologyportal/sigmanlp/actions/workflows/test-report.yml/badge.svg)

Installation
============

First follow the instructions to install sigmakee at https://github.com/ontologyportal/sigmakee.\
For macOS, replace .bashrc with .zshrc

```sh
cd ~
echo "## SigmaNLP" >> .bashrc
echo "export CORPORA=\$ONTOLOGYPORTAL_GIT/sigmanlp/corpora" >> .bashrc
echo "export SIGMANLP_CP=\$ONTOLOGYPORTAL_GIT/sigmanlp/build/sigmanlp.jar:$ONTOLOGYPORTAL_GIT/sigmanlp/lib/*" >> .bashrc
source ~/.bashrc
cd ~/workspace/
git clone https://github.com/ontologyportal/sigmanlp
cd ~/workspace/sigmanlp
ant get.stanford.corenlp.models
ant
```

To keep this repository updated
```sh
ant update.sigmanlp
```

Then follow the steps in "Account Management" below before proceeding

Account Management
==================

Add the following line to your $SIGMA_HOME/KBs/config.xml file, but replace '~' with the full path
and "latest" with you're version of stanford-corenlp:

```xml
<preference name="englishPCFG" value="~/Programs/stanford-corenlp-latest/stanford-corenlp-latest-models.jar"/>
```

** NOTE: If you see a java.io.StreamCorruptedException being thrown in the
   output console, then comment out the above "preference" element from your
   $SIGMA_HOME/KBs/config.xml file. SigmaNLP will work without that particular
   element

If you want to run sigmanlp's web interface then:
```sh
ant dist
```

Start Tomcat with:
```sh
$CATALINA_HOME/bin/startup.sh
```
http://localhost:8080/sigmanlp/NLP.jsp

If you want to make a link to the NLP tools available from Sigma's various jsp pages then include
the following in your config.xml

```xml
<preference name="nlpTools" value="yes" />
```

To run on the command line, try
```sh
java -Xmx10g -Xss1m -cp $ONTOLOGYPORTAL_GIT/sigmanlp/build/classes:$ONTOLOGYPORTAL_GIT/sigmanlp/lib/* com.articulate.nlp.semRewrite.Interpreter -i
```

jUnit
=====
```sh
java -Xmx10g -Xss1m -cp /home/user/workspace/sigmanlp/build/classes:
/home/user/workspace/sigmanlp/build/lib/*:/home/user/workspace/sigmanlp/lib/*
org.junit.runner.JUnitCore com.articulate.nlp.semRewrite.RunAllUnitSemRewrite

java -Xmx10g -Xss1m -cp /home/user/workspace/sigmanlp/build/classes:
/home/user/workspace/sigmanlp/build/lib/*:/home/user/workspace/sigmanlp/lib/*
org.junit.runner.JUnitCore com.articulate.nlp.semRewrite.RunAllSemRewriteIntegTest
```

IDE
===

To build/run/debug/test using the NetBeans IDE, define a
nbproject/private/private.properties file with these keys:

    # private properties
    javaapis.dir=${user.home}/javaapis
    workspace=${javaapis.dir}/INSAFE

    # The default installation space is: ~/workspace. However, it can be
    # anywhere on your system as long as you define the "workspace" key above.

    catalina.home=${path.to.your.tomcat9}

    # JavaMail properties
    user=${your.email.user.name}
    my.email=${user}@${your.email.domain}
    my.name=${your.name}
