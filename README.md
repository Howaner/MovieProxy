# MovieProxy

[MovieProxy](https://www.movieproxy.de) ist eine Webapplikation zum Selbst-Hosting mit der Möglichkeit, Videos gleichzeitig herunterzuladen und anzuschauen.

![Preview](https://www.movieproxy.de/images/preview.png)

## Funktionsweise

Sobald die Software gestartet wird, wird ein HTTP Server auf Port 8080 gestartet, über den das Webinterface erreicht werden kann. Mehr unter [Installation](https://www.movieproxy.de/installation/).

## Download

Die Software kann auf meinem Jenkins heruntergeladen werden oder selbst kompiliert werden.  
Download: https://ci.howaner.de/job/MovieProxy/  
Mehr Informationen auf der [Homepage](https://www.movieproxy.de/installation/).

## Kompilierung

Das Projekt verwendet Maven als Build-Management-Software. Zum Kompilieren muss also nur der Maven Build gestartet werden.  
```mvn clean install```

## Verwendete Libraries

- Netty
- Gson
- Guava
- Lombok
- Log4j
- Junit
