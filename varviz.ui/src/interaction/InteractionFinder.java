package interaction;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.JFrame;

import cmu.conditional.Conditional;
import cmu.varviz.trace.Edge;
import cmu.varviz.trace.Method;
import cmu.varviz.trace.MethodElement;
import cmu.varviz.trace.Statement;
import cmu.vatrace.FieldPutStatement;
import cmu.vatrace.LocalStoreStatement;
import de.fosd.typechef.featureexpr.FeatureExpr;
import de.fosd.typechef.featureexpr.SingleFeatureExpr;
import interaction.controlflow.ControlflowControl;
import interaction.dataflow.DataVar;
import interaction.dataflow.GeneralDataInteraction;
import interaction.dataflow.VarInteractionControl;
import interaction.spec.SpecControl;
import interaction.spec.Specification;
import interaction.spec.XMLReader;
import interaction.types.ControInteraction;
import interaction.types.VarInteraction;
import interaction.view.ClickHandler;
import interaction.view.DotGraph;
import interaction.view.InteractGraph;
import interaction.view.SpecDialog;

/**
 * has...
 * 
 * @author Larissa Rocha
 *  
 */

public class InteractionFinder {
	
	List<LocalStoreStatement> varList = new ArrayList<>();
	List<DataVar> dataVarList = new ArrayList<>();	//list with all the vars and their expressions
	List<FeatureExpr> dataExpressions = new ArrayList<>();//list with all data expressions
	private ArrayList<Specification> specList = new ArrayList<>();
	private Boolean isSpecOn = false;
	
	private Method mainMethod;
	private List<Edge> edges;
	
	public InteractionFinder(Method mainMethod, List<Edge> ed) {
		super();
		this.mainMethod = mainMethod;
		this.edges = ed;
	}

	public ArrayList<Specification> getSpecList() {
		return specList;
	}
	
	public void findInteractions(File workingDir){
		//gets all the variables and its context (presence conditions): dataVarList (name, expressions)
		collectVarExpressions();
		
		//getting all expressions from data
		for(DataVar var: dataVarList){
			dataExpressions.addAll( var.getCtxList());
		}
		
		//get data flow interactions in general
		GeneralDataInteraction generalDataI = new GeneralDataInteraction();
		List<VarInteraction> generalInteractionDataList = generalDataI.getDATAGeneralInte(dataVarList);
		
		//to analyze suppress and enable only in vars
		VarInteractionControl varInt = new VarInteractionControl();
		List<VarInteraction> interactionsPerVarList = varInt.findInteractionsPerVar(dataVarList);
		
		//control and data flow analysis
		getImplications(workingDir,interactionsPerVarList,generalInteractionDataList);
		
	}
	


	public void getImplications(File workingDir, List<VarInteraction> interactionsPerVarList, List<VarInteraction> generalInteractionDataList) {
		
		List<FeatureExpr> expressions = new ArrayList<>();		//control + data interactions ???
		for (Edge edge : this.edges) {
			FeatureExpr ctx = edge.getCtx();
			
			if (!ctx.isTautology()) {				
				if (!expressions.contains(ctx)) {
					expressions.add(ctx);
					System.out.println(ctx);								
				}
			}
		}
			
		//analyzing all interactions together: control + data exp
		GeneralDataInteraction generalDataI = new GeneralDataInteraction();
		//List<VarInteraction> generalInteractionALLList = generalDataI.getALLInte(expressions, dataVarList);
		
		//create the complete set of expressions (control + data)
		List<FeatureExpr> allAxpressions = new ArrayList<>();
		allAxpressions.addAll(expressions);	
		List<FeatureExpr> listofAVAR;
		for(DataVar var: dataVarList){		
			listofAVAR = var.getCtxList();
			for(FeatureExpr expVar: listofAVAR) {
				if(!allAxpressions.contains(expVar)) {
					allAxpressions.add(expVar);
				}
			}			
		}
//		List<FeatureExpr> allAxpressions = new ArrayList<>();	
//		allAxpressions = generalDataI.getExpressionALL();
		
		ControlflowControl finder = new ControlflowControl();
		
		Map<PairExp, List<String>> hashMap = finder.getInteractionsTable(allAxpressions);//has everybody that interacts
		
		//relationships from presence conditions
		List<ControInteraction> controlFlowInteracList = finder.getInteractionList();
				
		if(isSpecificationOn()){
			treatSpecification(finder);
		}
	
		
		InteractGraph g = new InteractGraph(interactionsPerVarList, specList, workingDir);		
		
		System.out.println(workingDir.getAbsolutePath());
		
		//it creates the resulting graph and already removes interactions from spec
		InteractionCreator resultingGraph = new InteractionCreator(hashMap, finder.getFeatures(), finder.getNoEffectlist(), allAxpressions,
				interactionsPerVarList, specList);
		
		int a = 0;
		ArrayList<Interaction> finalList = resultingGraph.getInteractionsToShow();
		ArrayList<Interaction> typeTOTALinteractionsList = resultingGraph.getInteractionsOFtypeTOTAL();//receives interactions that are not partial
		
		printFinalList(finalList, resultingGraph);
		
		//creating dot graph
		DotGraph dot = new DotGraph();
		dot.createGraph(finalList, resultingGraph, workingDir, expressions);
		GraphFile file = new GraphFile();
		file.createFile(finalList, resultingGraph, workingDir, expressions, typeTOTALinteractionsList);
		try {
			file.write(finalList, resultingGraph, workingDir, expressions);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		//creating JgraphX
		List<String> finalString = file.getFileList();
		List<String> edgestoGraphx = file.getEdgestoGraphx();
		edgestoGraphx = preProcessingEdges(edgestoGraphx); //remove repeated pairs
		List<String> reducedEdges = file.getEdgestoGraphxREDUCED();//edgestoGraphx without vars of interactions of type total
		List<String> reducedEdges2 = removeDuplicatedInter(reducedEdges); //the same interaction but vars different, maintain just 1 inter with all vars
//		createJGraphX(finalString, resultingGraph,edgestoGraphx);
		
		System.out.println("");
		System.out.println("--------Printing reduced");
		for(int i = 0; i<reducedEdges2.size(); i++) {
			String s = reducedEdges2.get(i);
			System.out.println(reducedEdges2.get(i) + " " + reducedEdges2.get(i+1) + " " + reducedEdges2.get(i+2) + " " + reducedEdges2.get(i+3));
			i = i +3;
		}
		createJGraphX(finalString, resultingGraph,reducedEdges2);
	}
	

	private List<String> removeDuplicatedInter(List<String> reducedEdges) {
		
		ArrayList<String> listsupport = new ArrayList<>();
		List<String> newlist = new ArrayList<>();
		listsupport.addAll(reducedEdges);
		
		for(int i=0; i<listsupport.size();i++){
			
			if(listsupport.get(i)==null) {
				i = i+3;
				continue;
			}
			
			String from = listsupport.get(i);
			String to = listsupport.get(i+1);
			String relation = listsupport.get(i+2);
			String[] vars1 = listsupport.get(i+3).split ("\n");
			int numberVars = vars1.length;
			
			ArrayList<String> vars = new ArrayList<>();
			for(String v: vars1) {
				vars.add(v);
			}
			
			
			for(int j=i+4; j<listsupport.size();j++) {
				if(from.equals(listsupport.get(j))  && to.equals(listsupport.get(j+1)) && relation.equals(listsupport.get(j+2) )) {
					String[] varslist = listsupport.get(j+3).split ("\n");
					for(int a = 0; a<varslist.length; a++) {
						if(!vars.contains(varslist[a])) {
							String update = listsupport.get(i+3) + "\n" + varslist[a];
							listsupport.set(i+3, update);	
							vars.add(varslist[a]);
						}
					}
					listsupport.set(j, null);
					listsupport.set(j+1, null);
					listsupport.set(j+2, null);
					listsupport.set(j+3, null);
				}
				j = j + 3;
			}
			i = i+3;
		}
		
		for(int q=0; q<listsupport.size();q++) {
			if(listsupport.get(q)!=null) {
				newlist.add(listsupport.get(q));
			}
		}
		return newlist;
	}

	private boolean listHasElement(List<String> newlist, String from, String to, String relation) {
		// TODO Auto-generated method stub
		return false;
	}

	private List<String> preProcessingEdges(List<String> edgestoGraphx) {
		List<String> newList = new ArrayList<>(); //Order: F1, F2, relation, variables
		List<String> pairsList = new ArrayList<>();
		for(int i=0; i<edgestoGraphx.size(); i++){
			String toNotRepeatPair = edgestoGraphx.get(i)+ edgestoGraphx.get(i+1)+edgestoGraphx.get(i+2);
			
			if(newList.isEmpty()){
				newList.add(edgestoGraphx.get(i));
				newList.add(edgestoGraphx.get(i+1));
				newList.add(edgestoGraphx.get(i+2));
				newList.add(edgestoGraphx.get(i+3));				
				pairsList.add(toNotRepeatPair);
			}else{
				if(!pairsList.contains(toNotRepeatPair)){
					newList.add(edgestoGraphx.get(i));
					newList.add(edgestoGraphx.get(i+1));
					newList.add(edgestoGraphx.get(i+2));
					newList.add(edgestoGraphx.get(i+3));				
					pairsList.add(toNotRepeatPair);
				}else{//the pars exists then update it
					for(int j=0; j<newList.size(); j++){
						if(toNotRepeatPair.equals(newList.get(j)+newList.get(j+1)+newList.get(j+2))){
							newList.set(j+3, newList.get(j+3)+ "\n" + edgestoGraphx.get(i+3));//updating
							break;
						}
						j = j + 3;
					}
				}
			}
			 i = i + 3;
		}
		return newList;
	}

	private void treatSpecification(ControlflowControl finder) {
		
		List<DataVar> specFromXMLList = new ArrayList<>();	
		XMLReader xmlread = new XMLReader();
		xmlread.read(finder);
		specList.addAll(xmlread.getSpec());
		
		
	}

	private Boolean isSpecificationOn() {
		
		SpecDialog dialog = new SpecDialog();
		isSpecOn = true;
		return dialog.askSpec();
	
	}

	private void createJGraphX(List<String> finalString, InteractionCreator resultingGraph, List<String> edgestoGraphx) {
		
		List<String> noeffectGraph =  new ArrayList<>();
		for (SingleFeatureExpr feature1 : resultingGraph.getNoEffectlist()) {//creates no effect list
			 String f = Conditional.getCTXString(feature1);
			noeffectGraph.add(f);		
		}
		
		
		//adding the other features
		List<String> featuresGraph =  new ArrayList<>();
		for (SingleFeatureExpr feature1 : resultingGraph.getFeatures()) {
			String f = Conditional.getCTXString(feature1);
			if(!noeffectGraph.contains(f)){
				if(!f.equals("True")){
					featuresGraph.add(f);
				}
				
			}
				
		}					
		
		List<List> allListGraph = new ArrayList<>();
		allListGraph.add(noeffectGraph);
		allListGraph.add(featuresGraph);
		allListGraph.add(edgestoGraphx);
		
		if(!isSpecOn){//in case of previous spec is not activated.
			specList = null;
		}
		
		ClickHandler frame = new ClickHandler(allListGraph, specList);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setSize(1200, 800);
		frame.setVisible(true);	
		
	}

	private void printFinalList(ArrayList<Interaction> finalList, InteractionCreator resultingGraph) {
		System.out.println("");
		System.out.println("---------------All Interactions---------------");
		for(Interaction inte: finalList){
			if(inte.getRelations()!=null){
				for(Relationship r: inte.getRelations()){
					System.out.print(Conditional.getCTXString(inte.getPair().getA()) + " " + r.getRelation() + " " + Conditional.getCTXString(inte.getPair().getB()));
					if(r.getVars()!=null){
						System.out.print(": vars ");
						for(String var: r.getVars()){
							System.out.print(var + ", ");
						}
					}
				}
				System.out.println("");
			}
			else{
				System.out.println(Conditional.getCTXString(inte.getPair().getA()) + " , " + Conditional.getCTXString(inte.getPair().getB()));
			}
			
		}
		
		System.out.println("-> No interacting: " );
		for(SingleFeatureExpr feature: resultingGraph.getDoNotInterctList()){
			System.out.print(Conditional.getCTXString(feature)+ ", ");
		}
		System.out.println("\n-> No effect: ");
		for(SingleFeatureExpr feature: resultingGraph.getNoEffectlist()){
			System.out.print(Conditional.getCTXString(feature)+ ", ");
		}
		
		//printing interactions removed from spec
		System.out.println("\n------> Removed from spec:");
		if(resultingGraph.getRemovedInteractions() !=null){
			for(Interaction inte: resultingGraph.getRemovedInteractions()){
				if(inte.getRelations()!=null){
					for(Relationship r: inte.getRelations()){
						System.out.print(Conditional.getCTXString(inte.getPair().getA()) + " " + r.getRelation() + " " + Conditional.getCTXString(inte.getPair().getB()));
						if(r.getVars()!=null){
							System.out.print(": vars ");
							for(String var: r.getVars()){
								System.out.print(var + ", ");
							}
						}
					}
					System.out.println("");
				}else{
					System.out.println(Conditional.getCTXString(inte.getPair().getA()) + " , " + Conditional.getCTXString(inte.getPair().getB()));
				}
			}
		}
	
	}

	private void setAllow(ControlflowControl finder, String s1, String s2) {
		SpecControl specControl = new SpecControl();
			
		SingleFeatureExpr[] a = getFeaturesSpec(finder, s1, s2);
		specList.add(specControl.createAllow(a[0], a[1]));
		
	}
	
	private void setReqAllow(ControlflowControl finder, String s1, String s2, String var) {
		SpecControl specControl = new SpecControl();
		
		SingleFeatureExpr[] a = getFeaturesSpec(finder, s1, s2);
		Specification spec = specControl.createAllowReq(a[0], a[1], var);
		specList.add(spec);
	
	}
	
	private void setSupAllow(ControlflowControl finder, String s1, String s2, String var) {
		SpecControl specControl = new SpecControl();
		
		SingleFeatureExpr[] a = getFeaturesSpec(finder, s1, s2);
		Specification spec = specControl.createAllowSup(a[0], a[1], var);
		specList.add(spec);
	
	}

	private SingleFeatureExpr[] getFeaturesSpec(ControlflowControl finder, String s1, String s2) {
		Collection<SingleFeatureExpr> feat = finder.getFeatures();
		
		SingleFeatureExpr[] a = new SingleFeatureExpr[2];
		int i = 0;
		for (SingleFeatureExpr feature1 : feat) {
			
			if(Conditional.getCTXString(feature1).equals(s1)){
				a[i] = feature1;
				i++;
			}
			else if(Conditional.getCTXString(feature1).equals(s2)){
				a[i] = feature1;
				i++;
			}
		}
		return a;
		
	}

	//getting all the variables of the trace
	public void collectVarExpressions() {
		List<MethodElement> children = this.mainMethod.getChildren();
		recursiveMethod(children.get(0));	
		
	}
	private void recursiveMethod(MethodElement methodElement){
		
			if (methodElement instanceof Method) {
				System.out.println(methodElement);
				List a = ((Method) methodElement).getChildren();
				
				for(int i=0; i<a.size(); i++){
					recursiveMethod((MethodElement) a.get(i));
				}
			} else {
				if(methodElement instanceof LocalStoreStatement || methodElement instanceof FieldPutStatement){
					//varList.add((LocalStoreStatement) methodElement);
					Statement var = (Statement) methodElement;
					String name = var.toString();
					name = name.substring(0,name.length()-3);
					Conditional<String> value = var.getValue();
					for ( Entry<String, FeatureExpr> v: value.toMap().entrySet()){
						DataVar data = new DataVar(name, Conditional.simplifyCondition(v.getValue()));
						if(!containsData(data,Conditional.simplifyCondition(v.getValue()))){
							dataVarList.add(data);
							System.out.println("Var: " + data.getName() + " value: " + v.getValue());
						}
					}
			
				}
			}
	}

	//used by the recursive method to see if dataVarList has the variable and its context
	private boolean containsData(DataVar data, FeatureExpr featureExpr) {
		
		for(int i=0; i<dataVarList.size(); i++){
			if(dataVarList.get(i).getName().equals(data.getName())){
				if(!dataVarList.get(i).hascontext(featureExpr)){
					dataVarList.get(i).setContext(featureExpr);
					System.out.println("Var: " + dataVarList.get(i).getName() + " value: " + featureExpr);
				}
				return true;
			}
		}
		return false;
	}
	




}


