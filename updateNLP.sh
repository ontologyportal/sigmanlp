../sigmakee/updateSigma.sh
cd $ONTOLOGYPORTAL_GIT/sigmanlp
git pull
echo "waiting 10 seconds"
sleep 10
curl -d userName="user" -d password="user" "http://localhost:8080/sigmanlp/NLP.jsp" > result.html

