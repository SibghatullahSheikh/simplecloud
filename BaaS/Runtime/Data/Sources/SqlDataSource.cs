// SqlDataProvider.cs
//

using System;
using System.Collections.Generic;
using System.Threading;
using NodeApi;

namespace SimpleCloud.Data.Sources {

    public sealed class SqlDataSource : DataSource {

        private string _schemaName;
        private string _connectionString;
        private Dictionary<string, string> _partitions;

        public SqlDataSource(Application app, string name, Dictionary<string, object> configuration)
            : base(app, name, configuration) {
            _schemaName = Script.Or((string)configuration["schemaName"], "dbo");

            _connectionString = (string)configuration["connectionString"];
            if (String.IsNullOrEmpty(_connectionString)) {
                Runtime.Abort("No connection string was specified in the configuration for the '%s' data source.", name);
            }

            _partitions = (Dictionary<string, string>)configuration["partitions"];
        }

        private string BuildInsertCommand(string tableName, DataRequest request, List<object> parameters) {
            StringBuilder sb = new StringBuilder();
            sb.Append("insert into ");
            sb.Append(tableName);
            sb.Append(" (");
            foreach (KeyValuePair<string, object> member in request.Item) {
                if (parameters.Count != 0) {
                    sb.Append(",");
                }
                sb.Append(member.Key);
                parameters.Add(member.Value);
            }
            sb.Append(") values (");
            for (int i = 0; i < parameters.Count; i++) {
                if (i != 0) {
                    sb.Append(",");
                }
                sb.Append("@");
                sb.Append(i);
            }
            sb.Append(")");

            return sb.ToString();
        }

        private string BuildUpdateCommand(string tableName, DataRequest request, List<object> parameters) {
            StringBuilder sb = new StringBuilder();
            sb.Append("update ");
            sb.Append(tableName);
            sb.Append(" set");
            foreach (KeyValuePair<string, object> member in request.Item) {
                sb.Append(" " + member.Key);
                sb.Append("=@");
                sb.Append(parameters.Count);

                parameters.Add(member.Value);
            }
            sb.Append(" where id = @");
            sb.Append(parameters.Count);
            parameters.Add(request.Query.ID);

            return sb.ToString();
        }

        protected override Task<object> ExecuteNonQuery(DataRequest request, Dictionary<string, object> options) {
            string tableName = _schemaName + "." + request.Query.Collection.Name;

            string command = null;
            List<object> parameters = new List<object>();

            if (request.Operation == DataOperation.Insert) {
                request.Item["id"] = request.Query.ID;
                command = BuildInsertCommand(tableName, request, parameters);
            }
            else if (request.Operation == DataOperation.Update) {
                request.Item["id"] = request.Query.ID;
                command = BuildUpdateCommand(tableName, request, parameters);
            }
            else if (request.Operation == DataOperation.Delete_) {
                command = "delete from " + tableName + " where id = @0";
                parameters.Add(request.Query.ID);
            }

            if (command != null) {
                Deferred<object> deferred = Deferred.Create<object>();
                GetSqlService(request).Sql(command, parameters)
                    .Done(delegate(object o) {
                        deferred.Resolve((int)o == 1);
                    })
                    .Fail(delegate(Exception e) {
                        deferred.Reject(e);
                    });

                return deferred.Task;
            }

            return Deferred.Create<object>(Script.Undefined).Task;
        }

        protected override Task<object> ExecuteQuery(DataRequest request, Dictionary<string, object> options) {
            DataQuery query = request.Query;
            string tableName = _schemaName + "." + request.Query.Collection.Name;

            object result = null;
            if (query.IsLookup) {
                string command = "select * from " + tableName + " where id = @0";

                Deferred<object> deferred = Deferred.Create<object>();
                GetSqlService(request).Sql(command, new object[] { query.ID })
                    .Done(delegate(object o) {
                        object[] items = (object[])o;
                        if ((items != null) && (items.Length != 0)) {
                            result = items[0];
                        }
                    })
                    .Fail(delegate(Exception e) {
                        deferred.Reject(e);
                    });

                return deferred.Task;
            }
            else {
                // TODO: Apply query

                string command = "select top 10 * from " + tableName;
                return GetSqlService(request).Sql(command, null);
            }
        }

        public SqlService GetSqlService(DataRequest request) {
            string connectionString = _connectionString;

            if ((_partitions != null) && (String.IsNullOrEmpty(request.Partition) == false)) {
                connectionString = _partitions[request.Partition];

                if (String.IsNullOrEmpty(connectionString)) {
                    throw new Exception("Unknown partition '" + request.Partition + "'.");
                }
            }

            return new SqlService(connectionString);
        }

        public override object GetService(Dictionary<string, object> options) {
            string connectionString = _connectionString;

            if ((_partitions != null) && (options != null)) {
                string partition = (string)options["partition"];
                connectionString = _partitions[partition];

                if (String.IsNullOrEmpty(connectionString)) {
                    throw new Exception("Unknown partition '" + partition + "' referred to for sql data source '" + Name + "'");
                }
            }

            return new SqlService(connectionString);
        }
    }
}
