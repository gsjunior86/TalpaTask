import joblib
import pandas
import sys
import pathlib



try:
    #csv_file = 'data.csv'
    csv_file = sys.argv[1]
    print(pathlib.Path(__file__).parent.absolute())
    file_path = str(pathlib.Path(__file__).parent.absolute()) + '/model.pkl'
    model = joblib.load(file_path)
    data_df = pandas.read_csv(csv_file)
    original_df = data_df
    data_df = data_df.drop(['timestamp'], axis=1)
    data_df['prediction'] = model.predict(data_df)
    data_df['timestamp'] = original_df['timestamp']
    destination = str(pathlib.Path(__file__).parent.absolute()) + '/result.csv'
    data_df.to_csv(destination)

except Exception as err:
    print(err)


