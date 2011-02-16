/*

   Derby - Class org.apache.derby.impl.sql.compile.DropSchemaNode

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to you under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

package	org.apache.derby.impl.sql.compile;

import org.apache.derby.iapi.sql.compile.CompilerContext;
import org.apache.derby.iapi.sql.conn.Authorizer;
import org.apache.derby.iapi.sql.conn.LanguageConnectionContext;
import org.apache.derby.iapi.sql.conn.StatementContext;
import org.apache.derby.iapi.sql.execute.ConstantAction;

import org.apache.derby.iapi.error.StandardException;

import org.apache.derby.iapi.services.sanity.SanityManager;
import org.apache.derby.iapi.reference.SQLState;

/**
 * A DropSchemaNode is the root of a QueryTree that represents 
 * a DROP SCHEMA statement.
 *
 */

public class DropSchemaNode extends DDLStatementNode
{
	private int			dropBehavior;
	private String		schemaName;

	/**
	 * Initializer for a DropSchemaNode
	 *
	 * @param schemaName		The name of the object being dropped
	 * @param dropBehavior		Drop behavior (RESTRICT | CASCADE)
	 *
	 */
	public void init(Object schemaName, Object dropBehavior)
		throws StandardException
	{
		initAndCheck(null);
		this.schemaName = (String) schemaName;
		this.dropBehavior = ((Integer) dropBehavior).intValue();
	}

	public void bindStatement() throws StandardException
	{
		
        LanguageConnectionContext lcc = getLanguageConnectionContext();
        StatementContext stx = lcc.getStatementContext();
		/* 
		** Users are not permitted to drop
		** the SYS or APP schemas.
		*/
        if (getDataDictionary().isSystemSchemaName(schemaName))
		{
			throw(StandardException.newException(
                    SQLState.LANG_CANNOT_DROP_SYSTEM_SCHEMAS, this.schemaName));
		}
		
        /* 
        ** In SQL authorization mode, the current authorization identifier
        ** must be either the owner of the schema or the database owner 
        ** in order for the schema object to be dropped.
        */
        if (isPrivilegeCollectionRequired())
        {
            String currentUser = stx.getSQLSessionContext().getCurrentUser();
            getCompilerContext().addRequiredSchemaPriv(schemaName, 
                currentUser,
                Authorizer.DROP_SCHEMA_PRIV);
        }
	}

	/**
	 * Convert this object to a String.  See comments in QueryTreeNode.java
	 * for how this should be done for tree printing.
	 *
	 * @return	This object as a String
	 */

	public String toString()
	{
		if (SanityManager.DEBUG)
		{
			return super.toString() +
				"dropBehavior: " + "\n" + dropBehavior + "\n";
		}
		else
		{
			return "";
		}
	}

	public String statementToString()
	{
		return "DROP SCHEMA";
	}

	// inherit generate() method from DDLStatementNode


	/**
	 * Create the Constant information that will drive the guts of Execution.
	 *
	 * @exception StandardException		Thrown on failure
	 */
	public ConstantAction	makeConstantAction() throws StandardException
	{
		return	getGenericConstantActionFactory().getDropSchemaConstantAction(schemaName);
	}
}
