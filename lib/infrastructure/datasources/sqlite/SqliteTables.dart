import 'package:fossil/domain/entities/PebbleDevice.dart';

final Map<Type, String> typeToTable = {
  PebbleDevice: TableNames.pebbledevice,
};

class TableNames {
  static String get pebbledevice => "pebbledevice";
}

class SqlCreateTables {
  // id is the address of the device
  static String createPebbleDevice() => '''
    CREATE TABLE
      ${TableNames.pebbledevice} (
        id TEXT PRIMARY KEY,
        name TEXT
      )''';
}
