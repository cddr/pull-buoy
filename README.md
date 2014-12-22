# pull-buoy

A command line utility for copying pull requests from one github repo to another

## Usage

```
export GITHUB_FROM_BASE="https://api.github.com/"
export GITHUB_FROM_TOKEN=[generate your own token]
export GITHUB_FROM_REPO="rails/rails"

export GITHUB_TO_BASE="https://github.big-corp.com/"
export GITHUB_TO_TOKEN=[generate another token]
export GITHUB_TO_REPO="rails/rails"

git clone git@github.com:cddr/pull-buoy.git
cd pull-buoy
lein do compile,uberjar
java -jar target/pull-buoy-SNAPSHOT.jar
```

## License

Copyright © 2014 Andy Chambers

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
