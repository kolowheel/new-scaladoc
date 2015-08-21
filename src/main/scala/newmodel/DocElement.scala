package newmodel

import newmodel.Ctor.Constructor

// for now
case class Comment(rawComment: String)

case class SourceFile(name: String)

sealed trait Tree

sealed trait Stat extends Tree

trait DEBUG extends Tree

//for now


sealed trait Defn extends Stat

object Defn {

  case class Object(name: newmodel.Term.Name,
                    templ: Template,
                    comment: Comment,
                    mods: Seq[Mod],
                    file: SourceFile) extends Defn

  case class Trait(name: newmodel.Type.Name,
                   templ: Template,
                   comment: Comment,
                   mods: Seq[Mod],
                   tparams: Seq[newmodel.Type.Param],
                   file: SourceFile) extends Defn

  case class Class(name: newmodel.Type.Name,
                   ctor: Option[Constructor],
                   comment: Comment,
                   tparams: Seq[newmodel.Type.Param],
                   mods: Seq[Mod],
                   file: SourceFile,
                   templ: Template,
                   companion: Option[Object]) extends Defn

  case class Type(mods: Seq[Mod],
                  name: newmodel.Type.Name,
                  tparams: Seq[newmodel.Type.Param],
                  body: newmodel.Type) extends Defn

}

case class Pkg(name: String,
               stats: Seq[Stat],
               comment: Comment,
               id: Seq[Tree]) extends Defn


case class Template(parents: Seq[Type.Name], stats: Seq[Stat])

sealed trait Ctor extends Stat

object Ctor {

  //ConstructorDoc
  case class Constructor(name: String,
                         paramss: Seq[Seq[Term.Param]],
                         comment: Comment) extends Ctor

}

sealed trait Decl extends Stat

object Decl {

  //  type Tpe = Seq[Tree]
  //ValDoc
  case class Val(name: String,
                 decltpe: newmodel.Type,
                 comment: Comment,
                 mods: Seq[Mod],
                 id: Seq[Tree]) extends Decl

  //VarDoc
  case class Var(name: String,
                 decltpe: newmodel.Type,
                 comment: Comment,
                 mods: Seq[Mod],
                 id: Seq[Tree]) extends Decl

  //MethodDoc
  case class Def(name: String,
                 decltpe: newmodel.Type,
                 paramss: Seq[Seq[Term.Param]],
                 tparams: Seq[newmodel.Type.Param],
                 comment: Comment,
                 mods: Seq[Mod],
                 id: Seq[Tree]) extends Decl

  case class Type(mods: Seq[Mod],
             name: newmodel.Type.Name,
             tparams: Seq[newmodel.Type.Param],
             bounds: newmodel.Type.Bounds) extends Decl

}


sealed trait Type extends Tree

object Type {

  case class Name(name: String, id: Seq[Tree]) extends Type


  case class Bounds(lo: Option[Type], hi: Option[Type]) extends Tree

  case class Param(mods: Seq[Mod],
                   name: Type,
                   tparams: Seq[Type.Param],
                   typeBounds: Type.Bounds,
                   viewBounds: Seq[Type],
                   contextBounds: Seq[Type]) extends Type

  //for Seq[Int] == Type.Apply(Type.Name("Seq"), List(Type.Name("Int")))
  case class Apply(tpe: Type, args: Seq[Type]) extends Type


  // A with B with C
  case class Compound(tpes: Seq[Type])

  sealed trait Arg extends Tree


}


sealed trait Term extends Tree

object Term {

  case class Name(name: String, id: Seq[Tree]) extends Term

  case class Param(name: String,
                   mods: Seq[Mod],
                   decltpe: Type) extends Term

  // todo add default val

}


sealed trait Mod extends Tree

object Mod {


  object Annot extends Mod

  object Private extends Mod

  // todo should be   class Private(within: Name.Qualifier)

  object Protected extends Mod

  object Implicit extends Mod

  object Final extends Mod

  object Sealed extends Mod

  object Override extends Mod

  object Case extends Mod

  object Abstract extends Mod

  object Covariant extends Mod

  object Contravariant extends Mod

  object Lazy extends Mod

  object ValParam extends Mod

  object VarParam extends Mod

  object Ffi extends Mod

}


