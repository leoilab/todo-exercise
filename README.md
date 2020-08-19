# TODO Api exercise

## Project description

This repository contains a simple todo REST api with the following endpoints(at: [http://localhost:8080](http://localhost:8080)):

```
Method | Path          | Payload          | Response                                |Description
-----------------------------------------------------------------------------------------------------------
GET    | /v1/todo      |         -        | [{ id: Int, name: String, done: Bool }] | List all the todo items
POST   | /v1/todo      | { name: String } | {}                                      | Create a new todo item
POST   | /v1/todo/{id} |         -        | {}                                      | Set a todo item to done
```

Start the api with `sbt run`

The api is built using the following libraries:

 * [zio](https://zio.dev/)
 * [tapir](https://github.com/softwaremill/tapir)
 * [doobie](https://tpolecat.github.io/doobie/)
 
It additionally uses the following libraries:

 * [http4s](https://http4s.org/)
 * [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc)
 * [cats](https://typelevel.org/cats/)
 * [cats-effect](https://typelevel.org/cats-effect/)
 * [scalatest](http://www.scalatest.org/)

## Exercise description

### Coding skills

The first part of the exercise is to extend the api with authentication and authorization.
 * Implement the following new api endpoints:

```
Method | Path          | Payload                                | Response          | Description
----------------------------------------------------------------------------------------------------------
POST   | /v1/auth      | { username: String, password: String } | { token: String } | Login
POST   | /v1/auth/new  | { username: String, password: String } |        {}         | Create a new user
```

 * Create the necessary sql schema, endpoints and business logic(including data validation).
 * Limit the access to the `/v1/todo` endpoints to authenticated users and authorize the access to the todo items(so each user can only access the todo items created by them)
 * *Optional* write tests to ensure you correctly implemented the specification
 
### Design skills
 
The current codebase has most of the logic in `Server.scala` and mixes a lot of different concerns in one file.
 
Refactor the project to make the code more readable and manageable.
 
*This part of the exercise is open ended, spend as much time on it as you feel comfortable with.*
