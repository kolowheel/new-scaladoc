import newmodel.Decl.Def
import newmodel.Defn.{Class, Object, Trait}
import newmodel._

import scala.reflect.NameTransformer


trait DocGenerator {
  def generate(root: Pkg): String
}

object PlainStringGenerator extends DocGenerator {
  override def generate(root: Pkg): String = {
    /* println("Root package")
     def modelHandler(doc: DocElement): String = {
       def valParamsToStr(inputs: Seq[ValueParam]) =
         inputs.foldLeft("") {
           (str, valueParam: ValueParam) =>
             val implicitStr = if (valueParam.isImplicit) "implicit" else ""
             str + implicitStr + " " + valueParam.name + ": " + valueParam.result.name

         }

       doc match {
         case Package(name, elems, _, _) =>
           "Package name : " + name + "\n" + elems.map(modelHandler).mkString("\n")
         case ClassDoc(name, elems, _, _, _) =>
           "Class name : " + name + "\n" + elems.map(modelHandler).mkString("\n")
         case MethodDoc(name, returnType, inputs, _, _, _, _, _) =>
           s"Method : def $name( ${valParamsToStr(inputs)} ): ${returnType.name}"
         case ConstructorDoc(name, inputs, _, _) =>
           s"Constructor : def $name(${valParamsToStr(inputs)})"
       }
     }
     modelHandler(root)*/
    ??? // todo adapt
  }
}


object LatexDocGenerator {
  def apply(root: Pkg) = {
    new LatexDocGenerator(Index(root)).generate(root)
  }
}

class LatexDocGenerator(index: Index) extends DocGenerator {


  def generateIndex(index: Index): String = {
    "\\newpage\n" +
      "Generated INDEX" + indexForObjects(index) + "\n" +
      "Generated INDEX" + indexForMethods(index) + "\n" +
      "Generated INDEX" + indexForClasses(index) + "\n" +
      "Generated INDEX" + indexForTraits(index) + "\n"
  }

  override def generate(root: Pkg) = {
    latexHeader + processDocTree(root) + generateIndex(index) + latexEnder
  }


  def processDocTree(root: Pkg): String = {
    extractAllPackages(root).map(processPackage).mkString("\n")
  }

  def extractAllPackages(root: Pkg) = {
    def loop(el: Pkg): Seq[Pkg] = el.stats.collect {
      case p: Pkg =>
        p +: loop(p)
    }.flatten
    loop(root) :+ root
  }

  def processPackage(pack: Pkg): String = {
    if (pack.stats.count {
      case e: Pkg => true
      case _ => false
    } != pack.stats.length) {
      val traits = processTraits(pack.stats.collect { case t: Trait => t })
      val objects = processObjects(pack.stats.collect { case o: Object => o })
      val classes = processClasses(pack.stats.collect { case c: Class => c })
      """
      \chapter{Package """ ++ pack.name ++ """}{
                                           """ ++
        hypertarget(pack, None) ++
        """
         }\hskip -.05in
         \hbox to \hsize{\textit{ Package Contents\hfil Page}}
         \vskip .13in
        """ ++
        objects.map(_._1).getOrElse("") ++ "\n" ++
        traits.map(_._1).getOrElse("") ++ "\n" ++
        classes.map(_._1).getOrElse("")
    } else {
      ""
    }
  }


  def processClasses(classes: Seq[Class]) = {
    if (classes.nonEmpty) {
      val (tex, nested) = genericProcess(classes, processClass, (e: Class) => dumpNested(e.name.name, e.templ))
      Some("\\hbox{{\\bf  Classes}}\n" ++ tex, nested)
    } else {
      None
    }
  }

  def processObjects(objects: Seq[Object]) = {
    if (objects.nonEmpty) {
      val (tex, nested) = genericProcess(objects, processObject, (e: Object) => dumpNested(e.name.name, e.templ))
      Some("\\hbox{{\\bf  Objects}}\n" ++ tex, nested)
    } else {
      None
    }
  }

  def processTraits(traits: Seq[Trait]) = {
    if (traits.nonEmpty) {
      val (tex, nested) = genericProcess(traits, processTrait, (e: Trait) => dumpNested(e.name.name, e.templ))
      Some("\\hbox{{\\bf  Traits}}\n" ++ tex, nested)
    } else {
      None
    }
  }

  def genericProcess[A <: Defn](elems: Seq[A],
                                process: (A, String) => String,
                                nested: A => (String, Seq[Stat])): (String, Seq[Stat]) = {
    val unwrpd: Seq[(A, String, Seq[Stat])] = elems.map { e =>
      nested(e) match {
        case (nestedTex, stats) => (e, nestedTex, stats)
      }
    }
    (unwrpd.map {
      case (trt, nstd, ignored) =>
        process(trt, nstd)
    }.mkString("\n"), unwrpd.flatMap(_._3))
  }

  def dumpParent(tpe: Type.Name) = {
    val p = index.getByLink(tpe.id)
    val (name: {def name: String; def id: Seq[Tree]}, tparams) = p match {
      case Some(o: Trait) => (o.name, o.tparams)
      case Some(o: Class) => (o.name, o.tparams)
      case Some(o: Object) => (o.name, Seq())
    }
    hyperlink(name, Some(name.name)) ++ dumpTypeParams(tparams)
  }


  def dumpTypeMember(tpe: Defn.Type): String = {
    val name = dumpType(tpe.name)
    val body = dumpType(tpe.body)
    s" \\item{type $name = $body}"
  }

  def dumpAbstractTypeMember(tpe: Decl.Type): String = {
    val name = dumpType(tpe.name)
    val tparams = dumpTypeParams(tpe.tparams)
    val bounds = dumpTypeBounds(tpe.bounds)
    s" \\item{type $name$tparams$bounds}"
  }

  def dumpTypeBounds(bounds: Type.Bounds): String = {
    val lo = bounds.lo.map(" >: " + dumpType(_)).getOrElse("")
    val hi = bounds.hi.map(" <: " + dumpType(_)).getOrElse("")
    s"$hi $lo"
  }

  def processTrait(trt: Trait, nested: String): String = {
    val name = trt.name.name
    val comment = trt.comment.rawComment
    val methodsSummary = processMethodsSummary(trt.templ.stats)
    val methods = trt.templ.stats.collect { case m: Def => processMethod(m) }.mkString("\n")
    val extnds = if (trt.templ.parents.isEmpty)
      ""
    else
      "extends"
    val typeAlias = trt.templ.stats.collect { case m: Defn.Type => dumpTypeMember(m) }.mkString("\n")
    val typeMembers = trt.templ.stats.collect { case m: Decl.Type => dumpAbstractTypeMember(m) }.mkString("\n")

    val mods = trt.mods.map(_.getClass.getSimpleName.toLowerCase).mkString(" ")
    val link = hypertarget(trt.name, Some(trt.name.name))
    val parent = trt.templ.parents.map(dumpParent).mkString(" with ")
    s"""
        \\entityintro{$name}{}{$comment}
        \\vskip .1in
        \\vskip .1in
        $link
        \\section{trait $name}{
        \\vskip .1in
        $comment
        \\subsection{Declaration}{
        \\begin{lstlisting}[frame=none]
        {$mods trait $name $extnds $parent}
        \\end{lstlisting}
        ${methodSection(methods)}
        ${typeSection(typeAlias, typeMembers)}
      """

  }

  def methodSection(methods: String): String = {
    if (methods.isEmpty) {
      ""
    } else
      s"""
        \\subsection{Methods}{
        \\vskip -2em
        \\begin{itemize}
        $methods
        \\end{itemize}
        }"""
  }

  def typeSection(typeAlias: String, typeMembers: String): String = {
    if (typeAlias.trim.isEmpty && typeMembers.trim.isEmpty) {
      ""
    } else
      s"""
       \\subsection{Type}{
       \\vskip -2em
       \\begin{itemize}
      $typeAlias
      $typeMembers
      \\end{itemize}
      }}
     """
  }


  def dumpNested(name: String, tmpl: Template): (String, Seq[Stat]) = {
    val maxLVL = 4
    def loop(pname: String, tmpl: Template, lvl: Int): (String, Seq[Stat]) = {
      val d = tmpl.stats.collect {
        case e: Object => (e.name, e.templ, e)
        case e: Trait => (e.name, e.templ, e)
        case e: Class => (e.name, e.templ, e)
      }.map { case (cname: {def name: String; def id: Seq[Tree]}, teamplate, stat) =>
        val prepend = if (lvl > maxLVL) pname + "#" else ""
        val item = s"\\item ${hyperlink(cname, Some(prepend ++ cname.name))}\n"
        val (childTex, childStat) = loop(cname.name, teamplate, lvl + 1)
        val wrapped = if (lvl < maxLVL && childStat.nonEmpty) "\\begin{enumerate}\n" + childTex + "\n\\end{enumerate}" else childTex
        (item ++ wrapped, stat +: childStat)
      }.unzip
      (d._1.mkString("\n"), d._2.flatten)
    }
    val d = loop(name, tmpl, 2)
    ("\\begin{enumerate}\n" ++
      d._1 ++
      "\n\\end{enumerate}", d._2)
  }

  def processObject(obj: Object, nested: String): String = {
    val name = obj.name.name
    val comment = obj.comment.rawComment
    val methods =
      obj.templ.stats.collect { case m: Def => processMethod(m) }.mkString("\n")
    val mods = obj.mods.map(_.getClass.getSimpleName.toLowerCase).mkString(" ")
    val typeAlias = obj.templ.stats.collect { case m: Defn.Type => dumpTypeMember(m) }.mkString("\n")
    val typeMembers = obj.templ.stats.collect { case m: Decl.Type => dumpAbstractTypeMember(m) }.mkString("\n")
    val link = hypertarget(obj.name, Some(obj.name.name))
    val extnds = if (obj.templ.parents.isEmpty)
      ""
    else
      "extends"
    val parent = obj.templ.parents.map(dumpParent).mkString(" with ")
    s"""
        \\entityintro{$name}{}{$comment}
        \\vskip .1in
        \\vskip .1in
        $link
        \\section{object $name}{
        \\vskip .1in
        $comment
        \\subsection{Declaration}{

        {$mods object $name $extnds $parent}
      ${methodSection(methods)}
      ${typeSection(typeAlias, typeMembers)}
      """
  }

  def processClass(cls: Class, nested: String): String = {
    val name = cls.name.name
    val comment = cls.comment.rawComment
    val methods =
      cls.templ.stats.collect { case m: Def => processMethod(m) }.mkString("\n")
    val mods = cls.mods.map(_.getClass.getSimpleName.toLowerCase).mkString(" ")
    val typeAlias = cls.templ.stats.collect { case m: Defn.Type => dumpTypeMember(m) }.mkString("\n")
    val typeMembers = cls.templ.stats.collect { case m: Decl.Type => dumpAbstractTypeMember(m) }.mkString("\n")
    val link = hypertarget(cls.name, Some(cls.name.name))
    val extnds = if (cls.templ.parents.isEmpty)
      ""
    else
      "extends"
    val parent = cls.templ.parents.map(dumpParent).mkString(" with ")
    s"""
        \\entityintro{$name}{}{$comment}
        \\vskip .1in
        \\vskip .1in
        $link
        \\section{object $name}{
        \\vskip .1in
        $comment
        \\subsection{Declaration}{
        {$mods object $name $extnds $parent}
      ${methodSection(methods)}
      ${typeSection(typeAlias, typeMembers)}
      """
  }


  def processMethodsSummary(methods: Seq[Tree]): String = {
    s"""
        \\subsection{Method summary}{
        \\begin{verse}
          ${methods.collect { case e: Def => s"{\\bf def ${e.name}(${dumpMethodInputs(e)})}\\\\" }.mkString("\n")}
        \\end{verse}
        }
      """
  }

  def dumpMethodInputs(e: Def): String = e.paramss.map(_.map(e => dumpType(e.decltpe)).mkString(", ")).mkString(")(")

  def commonIndex(elems: Seq[ {def name: String; def id: Seq[Tree]}]): String = {
    "\\begin{multicols}{2}\\noindent\n" +
      elems.map(e => s"{${hyperlink(e, Some(e.name))}\\\\}").mkString("\n") + "\n" +
      "\\end{multicols}"
  }

  def indexForMethods(index: Index): String = {
    commonIndex(index.defs)
  }

  def indexForObjects(index: Index): String = {
    commonIndex(index.objects.map(_.name))
  }

  def indexForClasses(index: Index): String = {
    commonIndex(index.classes.map(_.name))
  }

  def indexForTraits(index: Index): String = {
    commonIndex(index.traits.map(_.name))
  }


  def dumpSignature(e: Def) = {
    e.paramss.map(_.map(e => e.name + " : " + dumpType(e.decltpe)).mkString(", ")).mkString(")(")
  }

  def dumpTypeParams(tps: Seq[Type.Param]) = {
    val params = tps.map(dumpType).mkString(", ")
    if (params.nonEmpty) {
      s"[$params]"
    } else ""
  }

  def dumpType(tpe: Type): String = {
    tpe match {
      case e: Type.Name => hyperlink(e, Some(e.name))
      case e: Type.Apply => dumpType(e.tpe) + "[" + e.args.map(dumpType).mkString(", ") + "]"
      case e: Type.Compound => e.tpes.map(dumpType).mkString("with ")
      case e: Type.Param => {
        val name = dumpType(e.name)
        val tparams = dumpTypeParams(e.tparams)
        val ctx = e.contextBounds.map(dumpType).map(": " + _).mkString(" ")
        val view = e.viewBounds.map(dumpType).map("<% " + _).mkString(" ")
        val bounds = dumpTypeBounds(e.typeBounds)
        "[" + name + tparams + bounds + ctx + view + "]"
      }
    }
  }

  def processMethod(m: Def): String = {
    val mods = m.mods.map(_.getClass.getSimpleName.toLowerCase.dropRight(1)).mkString(" ")
    val name = m.name
    val returnType = dumpType(m.decltpe)
    val comment = m.comment.rawComment
    val signature = dumpSignature(m)
    val tparams = dumpTypeParams(m.tparams)
    val methodRef = hypertarget(m, None)
    val linkId = link(m.id)
    s"""
        \\item{
        \\index{$linkId}
        {\\bf  $methodRef}
            $mods def $name$tparams($signature) : $returnType
        \\begin{itemize}
        \\item{
        {\\bf  Description}
         $comment
        }
        \\end{itemize}}
      """
  }


  def latexHeader = {
    val slash = '\\'
    val dollar = '$'
    s"""
     ${slash}documentclass[11pt,a4paper]{report}
     ${slash}usepackage{color}
     ${slash}usepackage{ifthen}
     ${slash}usepackage{makeidx}
     ${slash}usepackage{ifpdf}
     ${slash}usepackage[headings]{fullpage}
     ${slash}usepackage{listings}
     ${slash}usepackage{multicol}
     \\lstset{language=Java,breaklines=true}
     \\ifpdf ${slash}usepackage[pdftex, pdfpagemode={UseOutlines},bookmarks,colorlinks,linkcolor={blue},plainpages=false,pdfpagelabels,citecolor={red},breaklinks=true]{hyperref}
       ${slash}usepackage[pdftex]{graphicx}
       \\pdfcompresslevel=9
       \\DeclareGraphicsRule{*}{mps}{*}{}
     \\else
       ${slash}usepackage[dvips]{graphicx}
     \\fi
     \\newcommand{\\entityintro}[3]{%
       \\hbox to \\hsize{%
         \\vbox{%
           \\hbox to .2in{}%
         }%
         {\\bf  #1}%
         \\dotfill\\pageref{#2}%
       }
       \\makebox[\\hsize]{%
         \\parbox{.4in}{}%
         \\parbox[l]{5in}{%
           \\vspace{1mm}%
           #3%
           \\vspace{1mm}%
         }%
       }%
     }
     \\newcommand{\\refdefined}[1]{
     \\expandafter\\ifx\\csname r@#1\\endcsname\\relax
     \\relax\\else
     {$dollar(${dollar}in \\ref{#1}, page \\pageref{#1}$dollar)$dollar}\\fi}
     \\date{\\today}
     \\chardef\\textbackslash=`\\\\
     \\makeindex
     \\begin{document}
     \\sloppy
     \\addtocontents{toc}{\\protect\\markboth{Contents}{Contents}}
     \\tableofcontents
      """
  }

  def hypertarget(e: {def id: Seq[Tree]}, text: Option[String]): String = {
    text.map(t => s"\\hypertarget{${link(e.id)}}{$t}").getOrElse("")
  }

  def hyperlink(e: {def id: Seq[Tree]}, text: Option[String]) = {
    text.map(t => s"\\hyperlink{${link(e.id)}}{$t}").getOrElse("")
  }

  def link(tpe: Seq[Tree]) = {
    val termToTerm = "."
    val termToType = "."
    val typeToType = "\\#"
    val typeToTerm = "\\#"
    def separtor(s: Tree, i: Int): String = if (s != tpe.last) {
      (s, tpe(i + 1)) match {
        case (e1: Term.Name, e2: Type.Name) => e1.name + termToType
        case (e1: Term.Name, e2: Term.Name) => e1.name + termToTerm
        case (e1: Type.Name, e2: Type.Name) => e1.name + typeToType
        case (e1: Type.Name, e2: Term.Name) => e1.name + typeToTerm
      }
    } else s match {
      case s: Type.Name => NameTransformer.encode(s.name)
      case s: Term.Name => NameTransformer.encode(s.name)
    }
    tpe.zipWithIndex.map {
      case (e, i) => separtor(e, i)
    }.mkString("")
  }


  def latexEnder: String = """
                            \printindex
                            \end{document}
                           """
}
