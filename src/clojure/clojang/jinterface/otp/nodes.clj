(ns clojang.jinterface.otp.nodes
  (:require [clojang.jinterface.otp :as otp]
            [clojang.util :as util])
  (:import [com.ericsson.otp.erlang
            AbstractNode
            OtpLocalNode
            OtpNode
            OtpMbox
            OtpPeer
            OtpSelf]))

;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
;;; OTP constructors
;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

(defn node
  "Constructor for ``OtpNode``."
  [node-name & args]
  (apply #'otp/init (into ['node node-name] args)))

(defn self
  "Constructor for ``OtpSelf``."
  [node-name & args]
  (apply #'otp/init (into ['self node-name] args)))

(defn peer
  "Constructor for ``OtpPeer``.

  Represents a remote OTP node. It acts only as a container for the nodename
  and other node-specific information that is needed by the OtpConnection
  class"
  [node-name & args]
  (apply #'otp/init (into ['peer node-name] args)))

;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
;;; OTP protocols
;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

(defprotocol AbstractNodeObject
  "Represents an OTP node.

  About nodenames: Erlang nodenames consist of two components, an alivename
  and a hostname separated by '@'. Additionally, there are two nodename
  formats: short and long. Short names are of the form ``alive@hostname``,
  while long names are of the form ``alive@host.fully.qualified.domainname``.
  Erlang has special requirements regarding the use of the short and long
  formats, in particular they cannot be mixed freely in a network of
  communicating nodes, however Jinterface makes no distinction. See the Erlang
  documentation for more information about nodenames.

  The constructors for the AbstractNode classes will create names exactly as
  you provide them as long as the name contains '@'. If the string you provide
  contains no '@', it will be treated as an alivename and the name of the local
  host will be appended, resulting in a shortname. Nodenames longer than 255
  characters will be truncated without warning.

  Upon initialization, this class attempts to read the file .erlang.cookie in
  the user's home directory, and uses the trimmed first line of the file as the
  default cookie by those constructors lacking a cookie argument. If for any
  reason the file cannot be found or read, the default cookie will be set to
  the empty string (""). The location of a user's home directory is determined
  using the system property ``user.home``, which may not be automatically set
  on all platforms.

  Instances of this class cannot be created directly, use one of the subclasses
  instead."
  (get-alivename [this]
    "Get the alivename part of the hostname.")
  (get-cookie [this]
    "Get the authorization cookie used by this node.")
  (create-transport [this addr port-num]
    "Create instance of ``OtpTransport``.")
  (create-server-transport [this port-num]
    "Create instance of ``OtpServerTransport``.")
  (get-hostname [this]
    "Get the hostname part of the nodename.")
  (get-name [this]
    "Get the name of this node.")
  (set-cookie [this value]
    "Set the authorization cookie used by this node.")
  (->str [this]
    "Return a string representation of the node."))

(extend-type AbstractNode AbstractNodeObject
  (get-alivename [this]
    (.alive this))
  (get-cookie [this]
    (.cookie this))
  (create-transport [this addr port-num]
    (.createTransport this addr port-num))
  (create-server-transport [this port-num]
    (.createServerTrasport this port-num))
  (get-hostname [this]
    (.host this))
  (get-name [this]
    (.node this))
  (set-cookie [this value]
    (.setCookie this value))
  (->str [this]
    (.toString this)))

(defprotocol LocalNodeObject
  "This class represents local node types. It is used to group the node types
  ``OtpNode`` and ``OtpSelf``."
  (create-pid [this]
    "Create an Erlang pid.")
  (create-port [this]
    "Create an Erlang port.")
  (create-ref [this]
    "Create an Erlang ref.")
  (get-port [this]
    "Get the port number used by this node.")
  ;; protected
  ; (get-epmd [this] "Get the EPMD socket.")
  ; (set-epmd [this socket] "Set the Epmd socket after publishing this nodes
  ;                          listen port to EPMD.")
  )

(extend-type OtpLocalNode LocalNodeObject
  (create-pid [this]
    (.createPid this))
  (create-port [this]
    (.createPort this))
  (create-ref [this]
    (.createRef this))
  (get-port [this]
    (.port this))
  ;; protected
  ; (get-epmd [this]
  ;   (.getEpmd this))
  ; (set-epmd [this socket]
  ;   (.setEpmd this socket))
  )

(defprotocol NodeObject
  "Represents a local OTP node. This class is used when you do not wish to
  manage connections yourself - outgoing connections are established as
  needed, and incoming connections accepted automatically. This class
  supports the use of a mailbox API for communication, while management of
  the underlying communication mechanism is automatic and hidden from the
  application programmer.

  Once an instance of this class has been created, obtain one or more
  mailboxes in order to send or receive messages. The first message sent to
  a given node will cause a connection to be set up to that node. Any
  messages received will be delivered to the appropriate mailboxes.

  To shut down the node, call ``(close)``. This will prevent the node from
  accepting additional connections and it will cause all existing
  connections to be closed. Any unread messages in existing mailboxes can
  still be read, however no new messages will be delivered to the mailboxes.

  Note that the use of this class requires that EPMD (Erlang Port Mapper
  Daemon) is running on each cooperating host. This class does not start EPMD
  automatically as Erlang does, you must start it manually or through some
  other means. See the Erlang documentation for more information about this."
  (close [this]
    "Close the node.")
  (close-mbox [this mbox] [this mbox reason]
    "Close the specified mailbox. If not reason is provided, the default
    reason of 'normal' is used.")
  (create-mbox [this] [this name]
    "Create a mailbox that can be used to send and receive messages with other,
    similar mailboxes and with Erlang processes. If a name name is not
    provided, the Mbox is simply unnamed.")
  (get-names [this] "Get a list of all known registered names on this node.")
  (ping [this node-name timeout] "Determine if another node is alive.")
  (register-mbox [this mbox-name mbox]
    "Register or remove a name for the given mailbox.")
  (register-status-handler [this handler]
    "Register interest in certain system events.")
  (set-flags [this flags])
  (whereis [this mbox-name]
    "Determine the pid corresponding to a registered name on this node."))

(extend-type OtpNode NodeObject
  (close [this]
    (.close this))
  (close-mbox
    ([this mbox]
      (.closeMbox this mbox))
    ([this mbox reason]
      (.closeMbox this mbox reason)))
  (create-mbox
    ([this]
      (.createMbox this))
    ([this name]
      (.createMbox this name)))
  (get-names [this]
    (.getNames this))
  (ping [this node-name timeout]
    (.ping this node-name timeout))
  (register-mbox [this mbox-name mbox]
    (.registerName this mbox-name mbox))
  (register-status-handler [this handler]
    (.registerStatusHandler this handler))
  (set-flags [this flags]
    (.setFlags this flags))
  (whereis [this mbox-name]
    (.whereis this mbox-name)))

(defprotocol SelfObject
  "Represents an OTP node. It is used to connect to remote nodes or accept
  incoming connections from remote nodes.

  When the Java node will be connecting to a remote Erlang, Java or C node, it
  must first identify itself as a node by creating an instance of this class,
  after which it may connect to the remote node.

  When you create an instance of this class, it will bind a socket to a port so
  that incoming connections can be accepted. However the port number will not
  be made available to other nodes wishing to connect until you explicitely
  register with the port mapper daemon by calling publishPort(). "
  (accept [this]
    "Accept an incoming connection from a remote node.")
  (connect [this peer]
    "Open a connection to a remote node.")
  (get-pid [this]
    "Get the Erlang PID that will be used as the sender id in all \"anonymous\"
    messages sent by this node.")
  (publish-port [this]
    "Make public the information needed by remote nodes that may wish to
    connect to this one.")
  (unpublish-port [this]
    "Unregister the server node's name and port number from the Erlang port
    mapper, thus preventing any new connections from remote nodes."))

(extend-type OtpSelf SelfObject
  (accept [this] (.accept this))
  (connect [this peer] (.connect this peer))
  (get-pid [this] (.pid this))
  (publish-port [this] (.publishPort this))
  (unpublish-port [this] (.unPublishPort this)))

;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
;;; Error handling
;;; >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

(util/add-err-handler #'otp/init
  [java.lang.IllegalArgumentException,
   java.lang.InstantiationException]
  "[ERROR] could not instantiate object!")
