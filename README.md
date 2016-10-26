## Installiation Instructions

* Build the project by executing 

```
$ mvn package
```

* Copy compiled plugin into Neo4j plugis folder

```
cp neo4j-auth/target/neo4j-auth-1.1.1.jar /path/to/neo4j/plugins/
```

* Edit neo4j configuration file. 

```
vi /path/to/neo4j/conf/neo4j-server.properties
```

* Add folowing line:

```
org.neo4j.server.thirdparty_jaxrs_classes=org.neo4j.extensions.server.unmanaged=/unmanaged
```

Plese pay attension for `extensions` word in the package name. Neo4j configuration file usually include sample line for such configration, but it will have different package name and server will not work.

 
* Start or restart neo4j 

## Usage

* Adding a new user:

curl --data "password={password}" --user neo4j:{neo4j.password} http://localhost:7474/api/auth/add/{user}

* Deleting a user:

curl --user neo4j:{neo4j.password} http://localhost:7474/api/auth/delete/{user}
 
## Neo4j Commands

* To disconnect from Neo4j console, execute:

:server disconnect

* To login back with different user, execute:

:server connect
