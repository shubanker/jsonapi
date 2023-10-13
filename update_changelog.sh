#!/bin/bash
set -euo pipefail

echo 123123
which docker > /dev/null || (echoerr "Please ensure that docker is installed" && exit 1)
echo 123123

cd -P -- "$(dirname -- "$0")" # switch to this dir
echo 123123

CHANGELOG_FILE=CHANGELOG.md
previous_version=$(head -5 $CHANGELOG_FILE | grep "##" | awk -F']' '{print $1}' | cut -c 5-)
echo $previous_version

# Remove the header so we can append the additions
tail -n +3 "$CHANGELOG_FILE" > "$CHANGELOG_FILE.tmp" && mv "$CHANGELOG_FILE.tmp" "$CHANGELOG_FILE"

if [[ -z ${GITHUB_TOKEN-} ]]; then
  echo "**WARNING** GITHUB_TOKEN is not currently set" >&2
  exit 1
fi

INTERACTIVE=""
if [[ -t 1 ]]; then
  INTERACTIVE="-it"
fi

docker run $INTERACTIVE --rm -v "$(pwd)":/usr/local/src/your-app ferrarimarco/github-changelog-generator -u stargate -p jsonapi -t $GITHUB_TOKEN --since-tag $previous_version --base $CHANGELOG_FILE --output $CHANGELOG_FILE --release-branch 'main' --exclude-labels 'duplicate,question,invalid,wontfix'

# Remove the additional footer added
head -n $(( $(wc -l < $CHANGELOG_FILE) - 3 )) $CHANGELOG_FILE > "$CHANGELOG_FILE.tmp" && mv "$CHANGELOG_FILE.tmp" "$CHANGELOG_FILE"
