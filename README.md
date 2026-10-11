# RouteOnFirstMatch for Apache NiFi

[![CI](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/actions/workflows/ci.yml/badge.svg)](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/actions/workflows/ci.yml)

A NiFi processor that sends each FlowFile to the **first** route whose condition is true.
You set the order the routes are checked in by dragging them in the processor's Advanced UI.

NiFi's built-in `RouteOnAttribute` either clones a FlowFile to every matching route or requires all
conditions to match. When conditions overlap and only the highest-priority one should win, you
otherwise end up chaining several `RouteOnAttribute` processors. RouteOnFirstMatch does it in one step.

## How it works

- Routes are added, edited and ordered in the processor's **Advanced** UI. Each route has a name, which
  becomes a relationship, and an Expression Language condition that returns `true` or `false`.
- Routes are evaluated in the order shown there, highest priority first.
- Evaluation stops at the first match. A FlowFile is never cloned.
- FlowFiles that match nothing go to `unmatched`.
- The chosen route is written to the `RouteOnFirstMatch.Route` attribute.

Example:

| Route    | Condition                       |
|----------|---------------------------------|
| `urgent` | `${priority:equals('urgent')}`  |
| `large`  | `${fileSize:gt(1000000)}`       |

With `urgent` above `large`, a FlowFile that is both urgent and large goes only to `urgent`.
Drag `large` above `urgent` and it goes only to `large`.

### Routes are set only in the Advanced UI

Right-click the processor and choose **Advanced**. The routes appear as a numbered list:

- use **+** to add a route;
- click a route to edit its name or condition;
- drag a route by its handle to change its priority;
- use a route's menu to delete it.

Every change is saved to the processor right away. The processor must be stopped to make changes.

The routes are not properties, so the configuration dialog does not show them. A property added there is
invalid, with a reminder to add the route in **Advanced** instead.

Conditions can use parameters, such as `${fileSize:gt(#{large.threshold})}`. A changed parameter value takes
effect the next time the processor is started, since NiFi only restarts processors whose properties use the parameter.

Outside the browser, the routes are the processor's `annotationData` in the REST API and in flow definitions, for example
`{"routes": [{"name": "urgent", "expression": "${priority:equals('urgent')}"}]}`.

## Compatibility

Requires NiFi 2.x (Java 21). The NAR is built against the NiFi version in the root `pom.xml` (currently 2.12.0).
It does not run on NiFi 1.x, which uses a different Java version, Jetty, and UI. For NiFi 1.x, use the
[`nifi-1.x` branch](https://github.com/dtrodrigues/nifi-route-on-first-match-bundle/tree/nifi-1.x), which builds the
same processor with versions ending in `-nifi1`.

To target a different NiFi 2.x release, change both the parent version and `nifi.version` in the root `pom.xml`, then refresh the
Advanced UI's copy of the NiFi theme so it matches that release, and rebuild:

```shell
./update-nifi-theme.sh          # copies the theme from the NiFi release named by nifi.version
```

The theme lives in `nifi-route-on-first-match-ui/src/main/frontend/src/nifi-shared`, and its `SOURCE` file records
which NiFi release it came from. Run `./update-nifi-theme.sh --help` for other options.

## Build

Requires Java 21 and Maven 3.9.16 or later (enforced by the NiFi parent pom). Maven downloads Node.js into `target/` to build the Advanced UI.

```shell
mvn clean install
```

The NAR is written to `nifi-route-on-first-match-nar/target/nifi-route-on-first-match-nar-<version>.nar`.

## Install

Copy the NAR into NiFi's `lib/` directory and restart NiFi.
Or drop it into the auto-load directory (`extensions/` by default), which needs no restart the first time.

To replace an installed NAR with one of the same version, such as a new `-SNAPSHOT` build, copy it over the old one
and restart NiFi. NiFi does not reload a NAR whose version is already loaded.

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
