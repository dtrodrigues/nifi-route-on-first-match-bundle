# RouteOnFirstMatch for Apache NiFi

A NiFi processor that sends each FlowFile to the **first** route whose condition is true.
You set the order the routes are checked in by dragging them in the processor's Advanced UI.

NiFi's built-in `RouteOnAttribute` either clones a FlowFile to every matching route or requires all
conditions to match. When conditions overlap and only the highest-priority one should win, you
otherwise end up chaining several `RouteOnAttribute` processors. RouteOnFirstMatch does it in one step.

## How it works

- Each user-defined property is a route. Its name becomes a relationship, and its value is an
  Expression Language condition that returns `true` or `false`.
- Routes are evaluated in the order given by the **Evaluation Order** property: route names, one
  per line, highest priority first. Routes left out of the list are evaluated after it, alphabetically.
- Evaluation stops at the first match. A FlowFile is never cloned.
- FlowFiles that match nothing go to `unmatched`.
- The chosen route is written to the `RouteOnFirstMatch.Route` attribute.

Example:

| Property           | Value                           |
|--------------------|---------------------------------|
| `urgent`           | `${priority:equals('urgent')}`  |
| `large`            | `${fileSize:gt(1000000)}`       |
| `Evaluation Order` | `urgent` and `large` on separate lines |

A FlowFile that is both urgent and large goes only to `urgent`. Move `large` above `urgent` and it goes only to `large`.

### Advanced UI

Right-click the processor and choose **Advanced**. The routes appear as a numbered list:

- drag a route by its handle to change its priority;
- click a route to edit its name or condition;
- use **+** to add a route, and a route's menu to delete it.

Every change is saved to the processor right away. The UI maintains the Evaluation Order property for you.
The processor must be stopped to make changes.

## Compatibility

Requires NiFi 2.x (Java 21). The NAR is built against the NiFi version in the root `pom.xml` (currently 2.12.0).
It does not run on NiFi 1.x, which uses a different Java version, Jetty, and UI.

To target a different NiFi 2.x release, change both the parent version and `nifi.version` in the root `pom.xml`, then refresh the
Advanced UI's copy of the NiFi theme so it matches that release, and rebuild:

```shell
./update-nifi-theme.sh          # copies the theme from the NiFi release named by nifi.version
```

The theme lives in `nifi-route-on-first-match-ui/src/main/frontend/src/nifi-shared`, and its `SOURCE` file records
which NiFi release it came from. Run `./update-nifi-theme.sh --help` for other options.

## Build

Requires Java 21 and Maven 3.9+. Maven downloads Node.js into `target/` to build the Advanced UI.

```shell
mvn clean install
```

The NAR is written to `nifi-route-on-first-match-nar/target/nifi-route-on-first-match-nar-<version>.nar`.

## Install

Copy the NAR into NiFi's `lib/` directory and restart NiFi.
Or drop it into the auto-load directory (`extensions/` by default), which needs no restart.

## Develop the Advanced UI

The Angular app is in `nifi-route-on-first-match-ui/src/main/frontend` and needs Node.js 24.

```shell
cd nifi-route-on-first-match-ui/src/main/frontend
npm ci
npm test           # unit tests
npm start          # dev server on http://localhost:4205, proxies API calls to NiFi at https://localhost:8443
```

To rebuild only the Java modules and reuse an existing frontend build, use:

```shell
mvn install -Dskip.installnodenpm -Dskip.npm
```

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

Apache NiFi and NiFi are trademarks of The Apache Software Foundation. This project is not affiliated
with or endorsed by The Apache Software Foundation.
