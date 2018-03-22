# Confluence Comment Generator #

Automates generating confluence comments.

## Building ##

1. Run ./gradlew installDist

## Running ##

1. By default, the files will be dropped in `build/install/confluence-comment-generator/bin/`
1. Run 'confluence-comment-generator -h' to see configuration options. 
1. On linux (or similar system), run 'read -q PASS' to save your password as a variable without displaying it
1. At a minimum you will need to supply a username, password and contentId (page/comment to post to)
1. E.g. `confluence-comment-generator -H confluence.example.com -u sgerber -p $PASS -c 69510930`

