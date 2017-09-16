package tethys.readers.instances

import tethys.JsonReader
import tethys.readers._
import tethys.readers.tokens.TokenIterator

import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.language.higherKinds
import scala.reflect.ClassTag

trait ComplexReaders extends LowPriorityComplexReaders {
  implicit def mapReader[K, A](implicit keyReader: KeyReader[K], jsonReader: JsonReader[A]): JsonReader[Map[K, A]] = new JsonReader[Map[K, A]] {
    override def read(it: TokenIterator)(implicit fieldName: FieldName): Map[K, A] = {
      if(it.currentToken().isObjectStart) recRead(it.next(), Map.newBuilder[K, A])(fieldName)
      else ReaderError.wrongType[Map[K, A]]
    }

    @tailrec
    private def recRead(it: TokenIterator, builder: mutable.Builder[(K, A), Map[K, A]])
                       (fieldName: FieldName): Map[K, A] = {
      it.currentToken() match {
        case token if token.isObjectEnd =>
          it.nextToken()
          builder.result()
        case token if token.isFieldName =>
          val name = it.fieldName()
          implicit val nextFieldName: FieldName = fieldName.appendFieldName(name)

          recRead(it, builder += keyReader.read(name) -> jsonReader.read(it.next()))(fieldName)

        case _ => ReaderError.wrongJson(fieldName)
      }
    }
  }

  implicit def optionReader[A](implicit jsonReader: JsonReader[A]): JsonReader[Option[A]] = new JsonReader[Option[A]] {

    override val defaultValue: Option[Option[A]] = Some(None)

    override def read(it: TokenIterator)(implicit fieldName: FieldName): Option[A] = {
      if(it.currentToken().isNullValue) {
        it.nextToken()
        None
      } else {
        Some(jsonReader.read(it))
      }
    }
  }
}

private[readers] trait LowPriorityComplexReaders {
  implicit def genTraversableReader[A, C[X] <: Traversable[X]](implicit
                                                               jsonReader: JsonReader[A],
                                                               cbf: CanBuildFrom[Nothing, A, C[A]],
                                                               classTag: ClassTag[C[A]]
                                                              ): JsonReader[C[A]] = new JsonReader[C[A]] {
    override def read(it: TokenIterator)(implicit fieldName: FieldName): C[A] = {
      if(it.currentToken().isArrayStart) recRead(0, it.next(), cbf())
      else ReaderError.wrongType[C[A]]
    }

    @tailrec
    private def recRead(i: Int, it: TokenIterator, builder: mutable.Builder[A, C[A]])
                       (implicit fieldName: FieldName): C[A] = {
      it.currentToken() match {
        case token if token.isEmpty => ReaderError.wrongJson
        case token if token.isArrayEnd =>
          it.nextToken()
          builder.result()
        case _ =>
          recRead(i + 1, it, builder += jsonReader.read(it)(fieldName.appendArrayIndex(i)))
      }
    }
  }
}