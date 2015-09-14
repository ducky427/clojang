clojang
=======

[![][clj-logo]][clj-logo]

[clj-logo]: resources/images/clj-logo.png

*A Clojure wrapper for Erlang's JInterface*

![Clojars Project](http://clojars.org/clojang/latest-version.svg)

##### Table of Contents

* [Introduction](#introduction-)
* [Usage](#dependencies-)
  * [Handle Exit](#handle-exit-)  
  * [Erlang-style Handler](#erlang-style-handler-)
* [Example](#usage-)
  * [A Simple Calculator in Clojure](#a-simple-calculator-in-clojure-)
  * [OTP Integration in LFE](#otp-integration-in-lfe-)
* [Erlang and JInterface](#erlang-and-jinterface-)
  * [A Note on Versions](#a-note-on-versions-)
  * [Setting Your Erlang's JInterface for Clojure](#setting-your-erlangs-jinterface-for-clojure-)


## Introduction [&#x219F;](#table-of-contents)

This library is a rewrite of the
[clojure-erlastic](https://github.com/awetzel/clojure-erlastic)
library from which it was originally forked. It differs in how the code is
organized, code for wrapping and working with the Erlang types, code for
wrapping Erlang Java classes to more closely resemble Clojure idoims, and
the use of the Pulsar library by
[Parallel Universe](http://docs.paralleluniverse.co/pulsar/).


## Usage [&#x219F;](#table-of-contents)

`port-connection` creates two channels that you can use to
communicate respectively in and out with the calling erlang port.
The objects you put or receive throught these channels are encoded
and decoded into erlang binary term following these rules :

- erlang atom is clojure keyword
- erlang list is clojure list
- erlang tuple is clojure vector
- erlang binary is clojure bytes[]
- erlang integer is clojure long
- erlang float is clojure double
- erlang map is clojure map (thanks to erlang 17.0)
- clojure string is erlang binary (utf8)
- clojure set is erlang list

For instance, here is a simple echo server :

```clojure
(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go (while true
    (>! out (<! in))))))
```

### Handle Exit [&#x219F;](#table-of-contents)

The channels are closed when the launching erlang application dies, so you just
have to test if `(<! in)` is `nil` to know if the connection with erlang is
still opened.  


### Erlang-style Handler [&#x219F;](#table-of-contents)

In Java you cannot write a function as big as you want (the compiler may fail),
and the `go` and `match` macros expand into a lot of code. So it can be
useful to wrap your server with an "erlang-style" handler.

Clojure-erlastic provide the function `(run-server initfun handlefun)`
allowing you to easily develop a server using erlang-style handler :

- the `init` function must return the initial state
- the `handle` function must return `[:reply response newstate]`, or `[:noreply newstate]`

The argument of the init function is the first message sent by the erlang port
after starting.

```clojure
(require '[clojure.core.async :as async :refer [<! >! <!! go]])
(require '[clojure-erlastic.core :refer [run-server log]])
(use '[clojure.core.match :only (match)])

(run-server
  (fn [_] 0)
  (fn [term state] (match term
    [:add n] [:noreply (+ state n)]
    [:rem n] [:noreply (- state n)]
    :get [:reply state state])))

(log "end application, clean if necessary")
```

## Example [&#x219F;](#table-of-contents)


### A Simple Calculator in Clojure [&#x219F;](#table-of-contents)

My advice to create a simple erlang/elixir server in clojure is to create a `project.clj` containing the clojure-erlastic dependency and other needed deps for your server, then use "lein uberjar" to create a jar containing all the needed files. 

> mkdir calculator; cd calculator

> vim project.clj

```clojure
(defproject calculator "0.0.1" 
  :dependencies [[clojure-erlastic "0.1.4"]
                 [org.clojure/core.match "0.2.1"]])
```

> lein uberjar

Then create your clojure server as a simple script 

> vim calculator.clj

```clojure
(require '[clojure.core.async :as async :refer [<! >! <!! go]])
(require '[clojure-erlastic.core :refer [port-connection log]])
(use '[clojure.core.match :only (match)])

(let [[in out] (clojure-erlastic.core/port-connection)]
  (<!! (go 
    (loop [num 0]
      (match (<! in)
        [:add n] (recur (+ num n))
        [:rem n] (recur (- num n))
        :get (do (>! out num) (recur num)))))))
```

Finally launch the clojure server as a port, do not forget the `:binary` and `{:packet,4}` options, mandatory, then convert sent and received terms with `:erlang.binary_to_term` and `:erlang.term_to_binary`.

> vim calculator.exs

```elixir
defmodule CljPort do
  def start, do: 
    Port.open({:spawn,'java -cp target/calculator-0.0.1-standalone.jar clojure.main calculator.clj'},[:binary, packet: 4])
  def psend(port,data), do: 
    send(port,{self,{:command,:erlang.term_to_binary(data)}})
  def preceive(port), do: 
    receive(do: ({^port,{:data,b}}->:erlang.binary_to_term(b)))
end
port = CljPort.start
CljPort.psend(port, {:add,3})
CljPort.psend(port, {:rem,2})
CljPort.psend(port, {:add,5})
CljPort.psend(port, :get)
6 = CljPort.preceive(port)
```

> elixir calculator.exs


### OTP Integration in LFE [&#x219F;](#table-of-contents)

If you want to integrate your clojure server in your OTP application, use the
`priv` directory which is copied 'as is'.

```bash
mix new myapp ; cd myapp
mkdir -p priv/calculator
vim priv/calculator/project.clj # define dependencies
vim priv/calculator/calculator.clj # write your server
cd priv/calculator ; lein uberjar ; cd ../../ # build the jar
```

Then use `"#{:code.priv_dir(:myapp)}/calculator"` to find correct path in your app.

To easily use your clojure server, link the opened port in a GenServer, to
ensure that if java crash, then the genserver crash and can be restarted by its
supervisor.

> vim lib/calculator.ex

```elixir
defmodule Calculator do
  use GenServer
  def start_link, do: GenServer.start_link(__MODULE__, nil, name: __MODULE__)
  def init(nil) do
    Process.flag(:trap_exit, true)
    cd = "#{:code.priv_dir(:myapp)}/calculator"
    cmd = "java -cp 'target/*' clojure.main calculator.clj"
    {:ok,Port.open({:spawn,'#{cmd}'},[:binary, packet: 4, cd: cd])}
  end
  def handle_info({:EXIT,port,_},port), do: exit(:port_terminated)

  def handle_cast(term,port) do
    send(port,{self,{:command,:erlang.term_to_binary(term)}})
    {:noreply,port}
  end

  def handle_call(term,_,port) do
    send(port,{self,{:command,:erlang.term_to_binary(term)}})
    result = receive do {^port,{:data,b}}->:erlang.binary_to_term(b) end
    {:reply,result,port}
  end
end
```

Then create the OTP application and its root supervisor launching `Calculator`.

> vim mix.exs

```elixir
  def application do
    [mod: { Myapp, [] },
     applications: []]
  end
```

> vim lib/myapp.ex

```elixir
defmodule Myapp do
  use Application
  def start(_type, _args), do: Myapp.Sup.start_link

  defmodule Sup do
    use Supervisor
    def start_link, do: :supervisor.start_link(__MODULE__,nil)
    def init(nil), do:
      supervise([worker(Calculator,[])], strategy: :one_for_one)
  end
end
```

Then you can launch and test your application in the shell : 

```
iex -S mix
iex(1)> GenServer.call Calculator,:get
0
iex(2)> GenServer.cast Calculator,{:add, 3}
:ok
iex(3)> GenServer.cast Calculator,{:add, 3}
:ok
iex(4)> GenServer.cast Calculator,{:add, 3}
:ok
iex(5)> GenServer.cast Calculator,{:add, 3}
:ok
iex(6)> GenServer.call Calculator,:get
12
```


## Erlang and JInterface [&#x219F;](#table-of-contents)


### A Note on Versions [&#x219F;](#table-of-contents)

JInterface is only guaranteed to work with the version of Erlang with which it
was released. The following version numbers are paired:

| Erlang Release | Erlang Version (erts) | JInterface |
|----------------|-----------------------|------------|
| 18.0           | 7.0                   | 1.6        |
| 17.5           | 6.4                   | 1.5.12     |
| 17.4           | 6.3                   | 1.5.11     |
| 17.3           | 6.2                   | 1.5.10     |
| 17.2           | 6.1                   | 1.5.9      |
| 17.1           | 6.1                   | 1.5.9      |
| 17.0           | 6.0                   | 1.5.9      |
| R16B03         | 5.10.4                | 1.5.8      |
| R16B02         | 5.10.3                | 1.5.8      |
| R16B01         | 5.10.2                | 1.5.8      |
| R16B           | 5.10.1                | 1.5.8      |
| R15B03         | 5.9.3                 | 1.5.6      |
| R15B02         | 5.9.2                 | 1.5.6      |
| R15B01         | 5.9.1                 | 1.5.6      |
| R15B           | 5.9                   | 1.5.5      |


### Setting Your Erlang's JInterface for Clojure [&#x219F;](#table-of-contents)

To ensure that your version of JInterface is ready for use by Clojure with your
version of Erlang, simply do this:

```
$ ERL_LIBS=/opt/erlang/18.0 JINTERFACE_VER=1.6 make jinterface
```

This will run the following command for you, behind the scenes:

```
$ mvn install:install-file \
-Durl=file:repo \
-DgroupId=com.ericsson.otp.erlang \
-DartifactId=otperlang \
-Dversion=1.6 -Dpackaging=jar \
-Dfile=/opt/erlang/18.0/lib/jinterface-1.6/priv/OtpErlang.jar
```

and install it for you automatically in your ``~/.m2/`` directory, just like
``lein`` does with downloaded Clojars.

As you can see, the ``make jinterface`` target requires Maven to be installed.
