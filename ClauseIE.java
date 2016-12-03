package cie_package;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import ClauseIE_USC_NLP.Clause;
import ClauseIE_USC_NLP.Constituent;
import ClauseIE_USC_NLP.DpUtils;
import ClauseIE_USC_NLP.IndexedConstituent;
import ClauseIE_USC_NLP.Options;
import ClauseIE_USC_NLP.ProcessConjunctions;
import ClauseIE_USC_NLP.Proposition;
import ClauseIE_USC_NLP.TextConstituent;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;

public class ClauseIE {

	public static final Set<GrammaticalRelation> EXCLUDE_COMP;
	static {
		HashSet<GrammaticalRelation> temp = new HashSet<GrammaticalRelation>();
		temp.add(EnglishGrammaticalRelations.AUX_MODIFIER);
		temp.add(EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER);
		temp.add(EnglishGrammaticalRelations.SUBJECT);
		temp.add(EnglishGrammaticalRelations.COPULA);
		temp.add(EnglishGrammaticalRelations.ADVERBIAL_MODIFIER);
		EXCLUDE_COMP = Collections.unmodifiableSet(temp);
	}

	public static final Set<GrammaticalRelation> INCLUDE_COMP;

	static {
		HashSet<GrammaticalRelation> temp = new HashSet<GrammaticalRelation>();
		temp.add(EnglishGrammaticalRelations.AUX_MODIFIER);
		temp.add(EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER);
		temp.add(EnglishGrammaticalRelations.NEGATION_MODIFIER);
		INCLUDE_COMP = Collections.unmodifiableSet(temp);
	}

	static Tree dependencyTree;
	static SemanticGraph sg;

	static LexicalizedParser lp;
	static LexicalizedParserQuery lpq;
	static TokenizerFactory<CoreLabel> tokenizerFactory;
	static List<Clause> clauses;
	static List<Proposition> propsitions;

	public static void init() {
		lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
		lpq = lp.parserQuery();
		tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
		clauses = new ArrayList<Clause>();
		propsitions = new ArrayList<Proposition>();
	}

	public static void print(Object o) {
		System.out.println(o);
	}

	public static String generate(Clause clause, int index) {
		Constituent constituent = clause.constituents.get(index);
		IndexedConstituent iconstituent = (IndexedConstituent) constituent;
		SemanticGraph subgraph = iconstituent.createReducedSemanticGraph();
		// DpUtils.removeEdges(subgraph, icontituent.getRoot());
		Set<IndexedWord> words = new TreeSet<IndexedWord>(subgraph.descendants(iconstituent.getRoot()));
		for (IndexedWord v : iconstituent.getAdditionalVertexes()) {
			words.addAll(subgraph.descendants(v));
		}
		if (iconstituent.isPrepositionalPhrase())
			words.remove(iconstituent.getRoot());

		StringBuffer result = new StringBuffer();
		String sep = "";
		if (iconstituent.isPrepositionalPhrase()) {
			result.append(iconstituent.getRoot().lemma());
			sep = " ";
		}

		for (IndexedWord word : words) {
			result.append(sep);
			result.append(word.lemma());
			sep = " ";
		}

		return result.toString();
	}

	public static void addSubjClause(List<IndexedWord> roots, List<Clause> clauses, IndexedWord subject,
			IndexedWord clauseRoot, boolean partmod) {

		Options options = new Options();

		List<SemanticGraphEdge> toRemove = new ArrayList<SemanticGraphEdge>();

		List<IndexedWord> ccs = ProcessConjunctions.getIndexedWordsConj(sg, dependencyTree, clauseRoot,
				EnglishGrammaticalRelations.CONJUNCT, toRemove, options);
//		 print(ccs.toString());
		// print(toRemove.toString());

		for (int i = 0; i < ccs.size(); i++) {
			IndexedWord root = ccs.get(i);
			List<SemanticGraphEdge> outgoingEdges = sg.getOutEdgesSorted(root);
			List<SemanticGraphEdge> incomingEdges = sg.getIncomingEdgesSorted(root);

			// print(outgoingEdges.toString());

			Clause clause = new Clause();
			clause.verb = -1;
			SemanticGraphEdge cp = DpUtils.findFirstOfRelation(outgoingEdges, EnglishGrammaticalRelations.COPULA);
			Set<IndexedWord> exclude = null;
			Set<IndexedWord> include = null;

			// print(cp);

			// Excludes Complement Relations
			// Includes Verb Relations
			if (cp != null) {
				exclude = DpUtils.exclude(sg, EXCLUDE_COMP, root);
				include = DpUtils.exclude(sg, INCLUDE_COMP, root);
			} else {
				exclude = new HashSet<IndexedWord>();
			}

//			SemanticGraphEdge rcmod = DpUtils.findFirstOfRelation(incomingEdges,
//					EnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER);

			// print(rcmod);

			Constituent constRoot = null;
			clause.verb = clause.constituents.size();
			constRoot = new IndexedConstituent(sg, root, Collections.<IndexedWord>emptySet(), exclude,
					Constituent.Type.VERB);

			clause.constituents.add(constRoot);

			clause.subject = clause.constituents.size();
			// if(subject.tag().charAt(0) == 'W'){
			// clause.complement.add(createRelCon)
			// }
			clause.constituents.add(new IndexedConstituent(sg, subject, Constituent.Type.SUBJECT));

			// print(clause.toString());

			for (SemanticGraphEdge outgoingEdge : outgoingEdges) {

				IndexedWord dependent = outgoingEdge.getDependent();
				if (DpUtils.isIobj(outgoingEdge)) {
					clause.iobjects.add(clause.constituents.size());
					clause.constituents.add(new IndexedConstituent(sg, dependent, Constituent.Type.IOBJ));
				} else if (DpUtils.isDobj(outgoingEdge)) {
					clause.dobjects.add(clause.constituents.size());
					clause.constituents.add(new IndexedConstituent(sg, dependent, Constituent.Type.DOBJ));
				} else if (DpUtils.isCcomp(outgoingEdge)) {
					clause.ccomps.add(clause.constituents.size());
					clause.constituents.add(new IndexedConstituent(sg, dependent, Constituent.Type.CCOMP));
				} else if (DpUtils.isAcomp(outgoingEdge)) {
					clause.acomps.add(clause.constituents.size());
					clause.constituents.add(new IndexedConstituent(sg, dependent, Constituent.Type.ACOMP));
				} else if ((DpUtils.isAnyPrep(outgoingEdge) || DpUtils.isPobj(outgoingEdge)
						|| DpUtils.isTmod(outgoingEdge) || DpUtils.isAdvcl(outgoingEdge)
						|| DpUtils.isNpadvmod(outgoingEdge) || DpUtils.isPurpcl(outgoingEdge))) {
					clause.adverbials.add(clause.constituents.size());
					clause.constituents.add(new IndexedConstituent(sg, dependent, Constituent.Type.ADVERBIAL));
				}

			}

			roots.add(root);
			if (!partmod) {
				clause.detectType(options);
			} else {
				clause.type = Clause.Type.SVA;
			}

			clauses.add(clause);
		}

	}

	public static void main(String a[]) throws IOException {
		
		init();
		
		String[] filenames = {"nyt_sentences.txt","reverb_sentences.txt","wiki_sentences.txt"};
		String[] out_filenames = {"nyt_out_sentences.txt","reverb_out_sentences.txt","wiki_out_sentences.txt"};
		
		String line = null;
		FileReader fileReader;
		BufferedReader bf;
		BufferedWriter bw;
		File file;
		String sentence = "Bell makes and distributes products";//electronic, computer and building
		
		for(int f = 0; f<3; f++){
			fileReader = new FileReader(filenames[f]);
			bf = new BufferedReader(fileReader);
			file = new  File(out_filenames[f]);
			bw = new BufferedWriter(new FileWriter(file));
			
			while((line = bf.readLine())!=null){
				clauses.clear();
				propsitions.clear();
				String[] pairs = line.split("\t",0);
				sentence = pairs[1];
				print(pairs[0]);
			
		
		

			// Generate Parser
			
	
	//		 String sentence = "Bell makes and distributes products";//electronic, computer and building
			// products.";
	//		String sentence = "What is and are your name?";
			// Parse String
			 
			
			 
			List<CoreLabel> tokenizedSentences = tokenizerFactory.getTokenizer(new StringReader(sentence)).tokenize();
			lpq.parse(tokenizedSentences);// getBestParse();
			dependencyTree = lpq.getBestParse();
			// List<Tree> trees = dependencyTree.getChildrenAsList();
			// print(trees.size());
			// Tree
	//		print(dependencyTree);
			sg = ParserAnnotatorUtils.generateUncollapsedDependencies(dependencyTree);
			// print(sg.getFirstRoot());
	
			List<IndexedWord> roots = new ArrayList<IndexedWord>();
//			 print(sg);
	
			// More sentences to generate different clause types
			for (SemanticGraphEdge edge : sg.edgeIterable()) {
				if (DpUtils.isAnySubj(edge)) {
					IndexedWord sub = edge.getDependent();
					IndexedWord root = edge.getGovernor();
					addSubjClause(roots, clauses, sub, root, false);
				}
			}
	
//			 for(Clause clause: clauses){
//				 print(clause);
//			 }
	
			
	
			List<List<Constituent>> constituents = new ArrayList<List<Constituent>>();
			List<Boolean> include = new ArrayList<Boolean>();
			List<List<Boolean>> includeConstituents = new ArrayList<List<Boolean>>();
	
			for (Clause clause : clauses) {
				constituents.clear();
	
				Proposition proposition = new Proposition();
				List<Proposition> pros = new ArrayList<Proposition>();
	
				if (clause.subject > -1) {
					proposition.constituents.add(generate(clause, clause.subject));
				}
	
				// if(clause.ver)
				// includes verb as well
				proposition.constituents.add(generate(clause, clause.verb));
	
				pros.add(proposition);
				SortedSet<Integer> sortedIndexes = new TreeSet<Integer>();
				sortedIndexes.addAll(clause.iobjects);
				sortedIndexes.addAll(clause.dobjects);
				sortedIndexes.addAll(clause.xcomps);
				sortedIndexes.addAll(clause.ccomps);
				sortedIndexes.addAll(clause.acomps);
				sortedIndexes.addAll(clause.adverbials);
				if (clause.complement >= 0)
					sortedIndexes.add(clause.complement);
				for (Integer index : sortedIndexes) {
					if (clause.constituents.get(clause.verb) instanceof IndexedConstituent
							&& clause.adverbials.contains(index)
							&& ((IndexedConstituent) clause.constituents.get(index)).getRoot()
									.index() < ((IndexedConstituent) clause.constituents.get(clause.verb)).getRoot()
											.index())
						continue;
					for (Proposition p : pros) {
						if (include.size() > 0  && index< include.size() && include.get(index)) {
							p.constituents.add(generate(clause, index));
						}
					}
				}
	
				// process adverbials before verb
				sortedIndexes.clear();
				sortedIndexes.addAll(clause.adverbials);
				for (Integer index : sortedIndexes) {
					if (clause.constituents.get(clause.verb) instanceof TextConstituent
							|| ((IndexedConstituent) clause.constituents.get(index)).getRoot()
									.index() > ((IndexedConstituent) clause.constituents.get(clause.verb)).getRoot()
											.index())
						break;
					if (include.size() > 0  && index< include.size() && include.get(index)) {
						for (Proposition p : pros) {
							p.constituents.add(generate(clause, index));
						}
					}
				}
	
				// make 3-ary if needed
				// included nary
				// if (!clausIE.options.nary ) {
				for (Proposition p : pros) {
					p.optional.clear();
					if (p.constituents.size() > 3) {
						StringBuilder arg = new StringBuilder();
						for (int i = 2; i < p.constituents.size(); i++) {
							if (i > 2)
								arg.append(" ");
							arg.append(p.constituents.get(i));
						}
						p.constituents.set(2, arg.toString());
						for (int i = p.constituents.size() - 1; i > 2; i--) {
							p.constituents.remove(i);
						}
					}
				}
				// }
	
				// we are done
				propsitions.addAll(pros);
			}
	
//			print(propsitions);
			
//			bw.write(pairs[1]+"\n");
//			if(Integer.parseInt(pairs[0]) > 60){
//				for(Clause clause: clauses){
//					bw.write(pairs[0]+"\t"+clause+"\n");
//				}
//			}else{
//					bw.write(clauses.size()+"\n");
//			}
			
			
//			bw.write(pairs[1]+"\n");
			
		}
	}

	}
}
