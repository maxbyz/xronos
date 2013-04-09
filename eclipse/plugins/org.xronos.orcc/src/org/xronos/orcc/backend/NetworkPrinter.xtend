/*
 * Copyright (c) 2012, Ecole Polytechnique Fédérale de Lausanne
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 *   * Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *   * Neither the name of the Ecole Polytechnique Fédérale de Lausanne nor the names of its
 *     contributors may be used to endorse or promote products derived from this
 *     software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
 
package org.xronos.orcc.backend

import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.List
import java.util.Map
import net.sf.orcc.df.Actor
import net.sf.orcc.df.Connection
import net.sf.orcc.df.Entity
import net.sf.orcc.df.Network
import net.sf.orcc.df.Port
import net.sf.orcc.graph.Vertex
import net.sf.orcc.ir.util.IrSwitch

/*
 * A VHDL Network printer
 * 
 * @author Endri Bezati
 */
class NetworkPrinter extends IrSwitch {
	
	var Network network;
	
	var Map<String,Object> options
	
	var String DEFAULT_CLOCK_DOMAIN = "CLK";
		/**
	 * Map which contains the Clock Domain of a port
	 */
	var Map<Port, String> portClockDomain;

	/**
	 * Map which contains the Clock Domain of an instance
	 */
	var Map<Actor, String> instanceClockDomain;

	/**
	 * Contains a Map which indicates the number of the broadcasted actor
	 */
	var Map<Connection, Integer> networkPortConnectionFanout;

	/**
	 * Contains a Map which indicates the number of a Network Port broadcasted
	 */
	var Map<Port, Integer> networkPortFanout;

	/**
	 * Contains a Map which indicates the index of the given clock
	 */

	var Map<String, Integer> clockDomainsIndex;

	var Map<Connection, List<Integer>> connectionsClockDomain;
	
	/**
	 * Count the fanout of the actor's output port
	 * 
	 * @param network
	 */
	
	def computeActorOutputPortFanout(Network network) {
		for (Vertex vertex : network.getVertices()) {
			if (vertex instanceof Actor) {
				var Actor actor = vertex as Actor;
				var Map<Port, List<Connection>> map = actor.getAdapter((typeof(Entity))).getOutgoingPortMap()
	
				for (List<Connection> values : map.values()) {
					var int cp = 0;
					for (Connection connection : values) {
						networkPortConnectionFanout.put(connection, cp);
						cp = cp + 1;
					}
				}
			}
		}
	}
	
	
	def computeNetworkInputPortFanout(Network network) {
		for (Port port : network.getInputs()) {
			var int cp = 0;
			for (Connection connection : network.getConnections()) {
				if (connection.getSource() == port) {
					networkPortFanout.put(port, cp + 1);
					networkPortConnectionFanout.put(connection, cp);
					cp = cp + 1;
				}
			}
		}
	}
	
	def void computeNetworkClockDomains(Network network,
			Map<String, String> clockDomains) {

		// Fill the the portClockDomain with "CLK" for the I/O of the network
		for (Port port : network.getInputs()) {
			portClockDomain.put(port, DEFAULT_CLOCK_DOMAIN);
		}

		for (Port port : network.getOutputs()) {
			portClockDomain.put(port, DEFAULT_CLOCK_DOMAIN);
		}

		// For each instance on the network give the clock domain specified by
		// the mapping configuration tab or if not give the default clock domain
		var int clkIndex = 0;
		clockDomainsIndex.put(DEFAULT_CLOCK_DOMAIN, clkIndex);
		clkIndex = clkIndex + 1;

		for (String string : clockDomains.values()) {
			if (!string.isEmpty() && !clockDomainsIndex.containsKey(string)) {
				clockDomainsIndex.put(string, clkIndex);
				clkIndex = clkIndex + 1;
			}
		}

		for (Vertex vertex : network.getVertices()) {
			if (vertex instanceof Actor) {
				var Actor actor = vertex as Actor;
				if (!clockDomains.isEmpty()) {
					if (clockDomains.keySet().contains(
							network.label+"_"+actor.getName())) {
						if (!clockDomains.get(network.label+"_"+actor.getName())
								.isEmpty()) {

							instanceClockDomain.put(actor, clockDomains
									.get(network.label+"_"+actor.getName()));
						}
					} else {
						instanceClockDomain.put(actor, DEFAULT_CLOCK_DOMAIN);
					}
				} else {
					instanceClockDomain.put(actor, DEFAULT_CLOCK_DOMAIN);
				}

			}
		}	
		if (clockDomainsIndex.size() > 1 && !instanceClockDomain.empty) {
			connectionsClockDomain = new HashMap<Connection, List<Integer>>();
			for (Connection connection : network.getConnections()) {
				if (connection.getSource() instanceof Port) {
					var List<Integer> sourceTarget = new ArrayList<Integer>();
					var int srcIndex = clockDomainsIndex.get(portClockDomain
							.get(connection.getSource()));
					var int tgtIndex = clockDomainsIndex
							.get(instanceClockDomain.get(connection
									.getTarget()));
					if (srcIndex != tgtIndex) {
						sourceTarget.add(0, srcIndex);
						sourceTarget.add(1, tgtIndex);
						connectionsClockDomain
								.put(connection, sourceTarget);
					}
				} else {
					if (connection.getTarget() instanceof Port) {
						var List<Integer> sourceTarget = new ArrayList<Integer>();
						var int srcIndex = clockDomainsIndex
								.get(instanceClockDomain.get(connection
										.getSource()));
						var int tgtIndex = clockDomainsIndex
								.get(portClockDomain.get(connection
										.getTarget()));
						if (srcIndex != tgtIndex) {
							sourceTarget.add(0, srcIndex);
							sourceTarget.add(1, tgtIndex);
							connectionsClockDomain.put(connection,
									sourceTarget);
						}
					} else {
						var List<Integer> sourceTarget = new ArrayList<Integer>();
						var int srcIndex = clockDomainsIndex
								.get(instanceClockDomain.get(connection
										.getSource()));
						var int tgtIndex = clockDomainsIndex
								.get(instanceClockDomain.get(connection
										.getTarget()));
						if (srcIndex != tgtIndex) {
							sourceTarget.add(0, srcIndex);
							sourceTarget.add(1, tgtIndex);
							connectionsClockDomain.put(connection,
									sourceTarget);
						}
					}

				}
			}
		}
	}
	
	def headerComments(){
		var dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		var date = new Date();
		'''
		-- ----------------------------------------------------------------------------
		-- __  ___ __ ___  _ __   ___  ___ 
		-- \ \/ / '__/ _ \| '_ \ / _ \/ __|
		--  >  <| | | (_) | | | | (_) \__ \
		-- /_/\_\_|  \___/|_| |_|\___/|___/
		-- ----------------------------------------------------------------------------
		-- Xronos synthesizer
		-- Top level Network: « network.simpleName» 
		-- Date: «dateFormat.format(date)»
		-- ----------------------------------------------------------------------------
		'''	
	}
	
	def printClockInformation(){
		'''
		-- ----------------------------------------------------------------------------
		-- Clock Domain(s) Information on the Network "«network.simpleName»"
		--
		-- Network input port(s) clock domain:
		«FOR port: network.inputs»
			--	«port.name» --> «portClockDomain.get(port)»
		«ENDFOR»
		-- Network output port(s) clock domain:
		«FOR port: network.outputs»
			-- 	«port.name» --> «portClockDomain.get(port)»
		«ENDFOR»
		-- Actor(s) clock domains:
		«FOR vertex: network.vertices»
			«IF vertex instanceof Actor»
				--	«(vertex as Actor).simpleName» («(vertex as Actor).simpleName») --> «instanceClockDomain.get(vertex as Actor)»
			«ENDIF»
		«ENDFOR»
		'''
	}
	
	def printLibrary(){
		var Boolean systemActors = false;
		for(Actor actor: network.children.filter(typeof(Actor))){
			if (actor.native){
				systemActors = true;
			}
		}
		'''
		library ieee;
		library SystemBuilder;
		«IF systemActors»library SystemActors;«ENDIF»
		
		use ieee.std_logic_1164.all;
		'''
	}
	
	def printEntity(){
		'''
		entity «network.simpleName» is
		port(
			 -- XDF Network Input(s)
			 «FOR port: network.inputs»
			 	«addDeclarationPort(port,"in","out",true)»
			 «ENDFOR»
			 -- XDF Network Output(s)
			 «FOR port: network.outputs»
			 	«addDeclarationPort(port,"out","in",true)»
			 «ENDFOR»
			 «IF options.containsKey("generateGoDone")»
			 	«FOR actor: network.children.filter(typeof(Actor))»
			 		«IF !actor.native»
			 			-- Instance «actor.simpleName» Actions Go and Done
			 			«FOR action: actor.actions»
			 				«actor.simpleName»_«action.name»_go : out std_logic;
			 				«actor.simpleName»_«action.name»_done : out std_logic;
			 			«ENDFOR»
			 		«ENDIF»
			 	«ENDFOR»
			 «ENDIF»
			 -- Clock(s) and Reset
			 «FOR string: clockDomainsIndex.keySet SEPARATOR "\n"»
			 «string» : in std_logic;
			 «ENDFOR»
			 RESET : in std_logic);
		end entity «network.simpleName»;
		'''
	}
	
	def addDeclarationPort(Port port, String dirA, String dirB, Boolean printRdy){
		'''
		«IF port.type.bool || port.type.sizeInBits == 1»
		«port.name»_data : «dirA» std_logic;
		«ELSE»
		«port.name»_data : «dirA» std_logic_vector(«port.type.sizeInBits - 1» downto 0);
		«ENDIF»
		«port.name»_send : «dirA» std_logic;
		«port.name»_ack : «dirB» std_logic;
		«IF printRdy»
		«port.name»_rdy : «dirB» std_logic;
		«ENDIF»
		«port.name»_count : «dirA» std_logic_vector(15 downto 0);
		'''
	}
	
	def printArchitecture(){
		'''
		architecture rtl of «network.simpleName» is
			-- --------------------------------------------------------------------------
			-- Internal Signals
			-- --------------------------------------------------------------------------
		
			-- Clock(s) and Reset signal
			signal clocks, resets: std_logic_vector(«clockDomainsIndex.size  - 1» downto 0);
		
			-- Network Input Port(s)
			«FOR port: network.inputs»
				«printSignal(port,"","ni",0,true)»
			«ENDFOR»
			
			-- Network Input Port Fanout(s)
			«FOR port: network.inputs»
				«printSignal(port,"","nif",networkPortFanout.get(port),true)»
			«ENDFOR»
			
			-- Network Output Port(s) 
			«FOR port: network.outputs»
				«printSignal(port,"","no",0,true)»
			«ENDFOR»
			
			-- Actors Input/Output and Output fanout signals
			«FOR actor: network.children.filter(typeof(Actor)) SEPARATOR "\n"»
				«FOR port: actor.inputs SEPARATOR "\n"»
					«printSignal(port,actor.simpleName+"_","ai",0,false)»
				«ENDFOR»
				
				«FOR port: actor.outputs SEPARATOR "\n"»
					«printSignal(port,actor.simpleName+"_","ao",0,true)»
					
					«printSignal(port,actor.simpleName+"_","aof",actor.getAdapter((typeof(Entity))).getOutgoingPortMap().get(port).size,true)»
				«ENDFOR»
			«ENDFOR»
		
			-- --------------------------------------------------------------------------
			-- Network Instances
			-- --------------------------------------------------------------------------
			«printArchitectureComponents()»
		
		begin
			-- Reset Controller
			rcon: entity SystemBuilder.resetController(behavioral)
			generic map(count => «clockDomainsIndex.size»)
			port map( 
			         clocks => clocks, 
			         reset_in => reset, 
			         resets => resets);
			
			«FOR clk: clockDomainsIndex.keySet»
				clocks(«clockDomainsIndex.get(clk)») <= «clk»;
			«ENDFOR»
		
			-- --------------------------------------------------------------------------
			-- Actor instances
			-- --------------------------------------------------------------------------
			«printInstanceConnection()»
			
			-- --------------------------------------------------------------------------
			-- Nework Input Fanouts
			-- --------------------------------------------------------------------------
			«FOR port: network.inputs»
				«addFannout(port, "ni", "nif", null)»
			«ENDFOR»
		
			-- --------------------------------------------------------------------------
			-- Actor Output Fanouts
			-- --------------------------------------------------------------------------
			«FOR actor: network.children.filter(typeof(Actor)) SEPARATOR "\n"»
				«FOR port: actor.outputs»
					«addFannout(port, "ao", "aof", actor)»
				«ENDFOR»
			«ENDFOR»
		
			-- --------------------------------------------------------------------------
			-- Queues
			-- --------------------------------------------------------------------------
			«FOR connection: network.connections SEPARATOR "\n"»
				«IF connection.source instanceof Port»
					«IF connection.target instanceof Actor»
						«addQeueu(connection.source as Port, connection.targetPort, null, connection.target as Actor, connection, "ai", "nif")»
					«ENDIF»
				«ELSEIF connection.source instanceof Actor»
					«IF connection.target instanceof Port»
						«addQeueu(connection.sourcePort, connection.target as Port, connection.source as Actor, null, connection, "no", "aof")»
					«ELSEIF connection.target instanceof Actor»
						«addQeueu(connection.sourcePort, connection.targetPort, connection.source as Actor, connection.target as Actor, connection, "ai", "aof")»
					«ENDIF»
				«ENDIF»
			«ENDFOR»
		
			-- --------------------------------------------------------------------------
			-- Network port(s) instantiation
			-- --------------------------------------------------------------------------
			
			-- Output Port(s) Instantiation
			«FOR port: network.outputs»
				«port.name»_data <= no_«port.name»_data;
				«port.name»_send <= no_«port.name»_send;
				no_«port.name»_ack <= «port.name»_ack;
				no_«port.name»_rdy <= «port.name»_rdy;
				«port.name»_count <= no_«port.name»_count;
			«ENDFOR»
			
			-- Input Port(s) Instantiation
			«FOR port: network.inputs»
				ni_«port.name»_data <= «port.name»_data;
				ni_«port.name»_send <= «port.name»_send;
				«port.name»_ack <= ni_«port.name»_ack;
				ni_«port.name»_count <= «port.name»_count;
			«ENDFOR»
		end architecture rtl;
		'''
	}
	
	def printSignal(Port port, String owner, String prefix, Integer fanout, Boolean printRdy){
		var String dataSize;
		if(port.type.bool || (port.type.sizeInBits == 1)){
			dataSize = "std_logic";
		}else{
			dataSize = "std_logic_vector("+(port.type.sizeInBits - 1)+" downto 0)";
		}
		var String fanoutSize;
		if(fanout == 0){
			fanoutSize = "std_logic";
		}else{
			fanoutSize = "std_logic_vector("+(fanout - 1)+" downto 0)";
		}
		'''
		«IF port.native»
			signal «port.name»_data : «dataSize»;
		«ELSE»
			signal «prefix»_«owner»«port.name»_data : «dataSize»;
			signal «prefix»_«owner»«port.name»_send : «fanoutSize»;
			signal «prefix»_«owner»«port.name»_ack : «fanoutSize»;
			«IF printRdy»signal «prefix»_«owner»«port.name»_rdy : «fanoutSize»;«ENDIF»
			signal «prefix»_«owner»«port.name»_count : std_logic_vector(15 downto 0);
		«ENDIF»
		'''
	}
	
	def printArchitectureComponents(){
		'''
		«FOR actor: network.children.filter(typeof(Actor)) SEPARATOR "\n"»
			«IF !actor.native»
			component «actor.simpleName» is
			port(
			     -- Instance «actor.simpleName» Input(s)
			     «FOR port: actor.inputs»
			     	«addDeclarationPort(port,"in","out", false)»
			     «ENDFOR»
			     -- Instance «actor.simpleName» Output(s)
			     «FOR port: actor.outputs»
			     	«addDeclarationPort(port,"out","in", true)»
			     «ENDFOR»
			     «IF options.containsKey("generateGoDone")»
			     	-- Instance «actor.simpleName» Actions Go and Done
			     	«FOR action: actor.actions SEPARATOR "\n"»
			     		«action.name»_go : out std_logic;
			     		«action.name»_done : out std_logic;
			    	«ENDFOR»
			     «ENDIF»
			     clk: in std_logic;
			     reset: in std_logic);
			end component «actor.simpleName»;
			«ENDIF»
		«ENDFOR»
		'''
	}
	
	def printInstanceConnection(){
		'''
		«FOR actor: network.children.filter(typeof(Actor)) SEPARATOR "\n"»
			«IF actor.native»
				-- «actor.simpleName» (System Actor)
				i_«actor.simpleName» : entity SystemActors.«actor.simpleName»(behavioral)
				«IF !actor.parameters.empty»
				generic map(
					-- Not currently supported
				)
				«ENDIF»
			«ELSE»
			i_«actor.simpleName» : component «actor.simpleName»
			«ENDIF»
			port map(
				-- Instance «actor.simpleName» Input(s)
				«FOR port: actor.inputs SEPARATOR "\n"»
					«addSignalConnection(actor, port, "ai", "In", true, null, false)»
				«ENDFOR»
				-- Instance «actor.simpleName» Output(s)
				«FOR port: actor.outputs SEPARATOR "\n"»
					«addSignalConnection(actor, port, "ao", "Out", true, null, true)»
				«ENDFOR»
				«IF options.containsKey("generateGoDone")»
					-- Instance «actor.simpleName» Actions Go and Done
					«FOR action: actor.actions SEPARATOR "\n"»
						«action.name»_go => «actor.simpleName»_«action.name»_go,
						«action.name»_done => «actor.simpleName»_«action.name»_done,
			    	«ENDFOR»
			    «ENDIF»
				-- Clock and Reset
				clk => clocks(«clockDomainsIndex.get(instanceClockDomain.get(actor))»),
				reset => resets(«clockDomainsIndex.get(instanceClockDomain.get(actor))»));
		«ENDFOR»
		'''
	}
	
	def addSignalConnection(Actor actor, Port port, String prefix, String dir, Boolean instConnection, Integer fanoutIndex, Boolean printRdy){
		var String owner = "";
		var String fanoutIndexString = "";
		if(actor != null){
			owner = actor.simpleName+"_";
		}
		if(fanoutIndex != null){
			fanoutIndexString = "("+fanoutIndex+")";
		}
		var String boolType = "";
		if((port.type.bool || (port.type.sizeInBits == 1)) && !instConnection){
			boolType = "(0)";
		}
		
		'''
		«IF port.native»
			«port.name»_data => «prefix»_«actor.simpleName»_«port.name»_data
		«ELSE»
			«IF instConnection»«port.name»«ELSE»«dir»«ENDIF»_data«boolType» => «prefix»_«owner»«port.name»_data,
			«IF instConnection»«port.name»«ELSE»«dir»«ENDIF»_send => «prefix»_«owner»«port.name»_send«fanoutIndexString»,
			«IF instConnection»«port.name»«ELSE»«dir»«ENDIF»_ack => «prefix»_«owner»«port.name»_ack«fanoutIndexString»,
			«IF printRdy»
				«IF instConnection»«port.name»«ELSE»«dir»«ENDIF»_rdy => «prefix»_«owner»«port.name»_rdy«fanoutIndexString»,
			«ENDIF»
			«IF instConnection»«port.name»«ELSE»«dir»«ENDIF»_count => «prefix»_«owner»«port.name»_count,
		«ENDIF»
		'''
	}

	def addFannout(Port port, String prefixIn, String prefixOut, Actor actor){
		var Integer fanoutDegree = 1;
		var String instanceName = "";
		if(actor != null){
			// Actor Output port fanout
			fanoutDegree = actor.getAdapter((typeof(Entity))).getOutgoingPortMap().get(port).size;
			instanceName = actor.simpleName + "_";
		}else{
			// Network Input port fanout
			fanoutDegree = networkPortFanout.get(port);
		}
		var Integer clkIndex = 0;
		if(actor != null){
			clkIndex = clockDomainsIndex.get(instanceClockDomain.get(actor));
		}else{
			clkIndex = clockDomainsIndex.get(portClockDomain.get(port));
		}
		'''
		f_«prefixIn»_«instanceName»«port.name» : entity SystemBuilder.Fanout(behavioral)
		generic map (fanout => «fanoutDegree», width => «port.type.sizeInBits»)
		port map(
			-- Fanout In
			«addSignalConnection(actor, port, prefixIn,"In", false, null, true)»
			-- Fanout Out
			«addSignalConnection(actor, port, prefixOut,"Out", false, null, true)»
			-- Clock & Reset
			clk => clocks(«clkIndex»),
			reset => resets(«clkIndex»));
		'''
	}
	
	def addQeueu(Port srcPort, Port tgtPort, Actor srcInstance, Actor tgtInstance, Connection connection, String prefixIn, String prefixOut){
		var Integer fifoSize = 1;
		if(!prefixIn.equals("no")){
				fifoSize = connection.size;
		}
		'''
		q_«prefixIn»_«IF tgtInstance !=null»«tgtInstance.simpleName»_«ENDIF»«tgtPort.name» : entity SystemBuilder.Queue(behavioral)
		generic map (length => «fifoSize», width => «tgtPort.type.sizeInBits»)
		port map(
			-- Queue Out
			«addSignalConnection(tgtInstance, tgtPort, prefixIn,"Out", false, null, false)»
			-- Queue In
			«addSignalConnection(srcInstance, srcPort, prefixOut,"In", false, networkPortConnectionFanout.get(connection), true)»
			-- Clock & Reset
			«IF connectionsClockDomain.containsKey(connection)»
				clk_i => clocks(«connectionsClockDomain.get(connection).get(0)»),
				reset_i => resets(«connectionsClockDomain.get(connection).get(0)»),
				clk_o => clocks(«connectionsClockDomain.get(connection).get(1)»),
				reset_o => resets(«connectionsClockDomain.get(connection).get(1)»));
			«ELSE»
				clk => clocks(0),
				reset => resets(0));
			«ENDIF»
		'''
	}
	
	
	def printNetwork(Network network, Map<String,Object> options){
		// Initialize members
		this.network = network; 
		this.options = options;
		networkPortFanout = new HashMap<Port, Integer>();
		networkPortConnectionFanout = new HashMap<Connection, Integer>();
		portClockDomain = new HashMap<Port, String>();
		instanceClockDomain = new HashMap<Actor, String>();
		clockDomainsIndex = new HashMap<String, Integer>();
		connectionsClockDomain = new HashMap<Connection,List<Integer>>();
		computeNetworkInputPortFanout(network);
		computeActorOutputPortFanout(network);
		var Map<String,String> clkDomains = new HashMap<String,String>(); 
		
		if (options.containsKey("clkDomains")) {
			clkDomains = options.get("clkDomains") as Map<String,String>; 
		}
		computeNetworkClockDomains(network,clkDomains);
		
		'''
		«headerComments()»
		
		«printClockInformation()»
		
		«printLibrary()»
		
		-- ----------------------------------------------------------------------------
		-- Entity Declaration
		-- ----------------------------------------------------------------------------
		«printEntity()»
		
		-- ----------------------------------------------------------------------------
		-- Architecture Declaration
		-- ----------------------------------------------------------------------------
		«printArchitecture()»
		-- ----------------------------------------------------------------------------
		-- ----------------------------------------------------------------------------
		-- ----------------------------------------------------------------------------
		'''
	}
	
}